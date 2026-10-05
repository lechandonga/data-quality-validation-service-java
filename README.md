# 数据质量校验与监控服务（内存态）

基于 Spring Boot 3 / Java 21 的数据质量校验与监控服务。数据集、契约、任务、指标、
告警全部保存在进程内存中，用于在不依赖外部存储的前提下，完整模拟并验证以下能力：

- 数据集契约注册（含非法/自相矛盾契约拒绝）与校验执行；
- 校验任务异步提交、状态查询、并发隔离；
- 规则执行异常归一化、失败不中断整批、不计为通过；
- 指标累积、时间窗口 + 基线对比告警、抖动去重收敛；
- 契约版本化，历史结论按提交时的契约快照可复现。

## 1. 契约模型

一个数据集（`datasetId`）持有一条只增不改的契约版本历史。每个契约版本包含若干字段约束
`FieldConstraint`，契约一旦注册即为不可变快照，更新契约只会追加新版本。

| 字段属性 | 含义 | 支持的字段类型 |
| --- | --- | --- |
| `name` | 字段名，同一契约内必须唯一且非空 | 全部 |
| `type` | 字段类型：`STRING` / `INTEGER` / `DECIMAL` / `BOOLEAN` / `TIMESTAMP` | 全部 |
| `required` | 必填：缺失、null 或空字符串判为违规 | 全部 |
| `unique` | 唯一：同一字段值在整个批次中出现多次即违规（每一行都报） | 全部 |
| `min` / `max` | 闭区间：数值型约束数值，`STRING` 约束字符串长度 | `INTEGER` / `DECIMAL` / `STRING` |
| `maxAgeMillis` | 时效：TIMESTAMP 业务时间距校验时刻允许的最大延迟（毫秒） | `TIMESTAMP` |

### 契约合法性规则（注册前强制校验）

注册前由 `ContractValidator` 收集全部问题后一次性拒绝（`INVALID_CONTRACT`，HTTP 400），
绝不静默接受，明细中逐条给出原因：

- 字段列表为空；字段名为空；字段未声明类型；字段名重复；
- 区间自相矛盾：`min > max`；
- 在 `BOOLEAN`/`TIMESTAMP` 等不支持的类型上配置区间；
- 字符串长度下界为负数；
- `maxAgeMillis` 配置在非 `TIMESTAMP` 字段，或阈值 ≤ 0。

## 2. 校验规则与结论模型

引擎按 `TYPE → REQUIRED → RANGE → UNIQUENESS → FRESHNESS` 的顺序执行规则：

- **多问题全报出**：同一条记录可同时命中多个约束，每条结论独立输出，通过
  `constraint`（`TYPE`/`REQUIRED`/`RANGE`/`UNIQUENESS`/`FRESHNESS`）区分原因，
  并附带字段、人类可读原因与实际值；
- **根因不重复报**：值无法按契约类型解释时只报 `TYPE`，区间/时效规则跳过同一根因；
- **整批汇总**：`recordCount`、`passedRecordCount`、`violatedRecordCount`、
  `violations`、`ruleErrors` 一并返回，并冻结 `contractVersion`。

## 3. 任务并发与隔离

- `POST /api/datasets/{id}/tasks?contractVersion=` 提交任务，立即返回 `taskId`，
  任务在线程池中异步执行，状态为 `PENDING` / `COMPLETED` / `FAILED`；
- 提交任务时**深拷贝当前数据快照**并冻结契约版本快照；同一数据集并发提交多个任务时，
  各自持有独立输入，任务执行期间数据集被改写或契约升级都不影响在途任务；
- 每次校验还会创建全新的规则实例（唯一性统计表等内部状态不跨任务共享）。

## 4. 规则异常归一化与错误分类

单条规则（含整批预处理）的执行由引擎在独立调用中守护，任何异常都被捕获并归一化为
`RuleErrorType`，**不中断整批任务、不把失败计为通过、不透出原始异常/堆栈/敏感信息**：

| 归一化错误类型 | 触发情形 |
| --- | --- |
| `RULE_EXECUTION_ERROR` | 规则内部抛出 NPE、算术异常、类转换等任何未预期异常 |
| `TYPE_COERCION_ERROR` | 值无法解释为契约类型 |
| `MISSING_FIELD_ERROR` | 规则依赖字段不存在 |
| `TIMEOUT_ERROR` | 单条规则超过时限（默认 1000 ms，见 `ValidationEngine`） |

HTTP 层错误码（`ErrorCode`）：`MALFORMED_REQUEST`（400）、`INVALID_CONTRACT`（400）、
`INVALID_ARGUMENT`（400）、`INVALID_RECORD`（400）、`NOT_FOUND`（404）、
`TASK_EXECUTION_ERROR`（500，未预期异常只返回通用提示，细节仅入服务日志）。

## 5. 指标与告警规则

每个任务完成后沉淀一条 `ValidationMetric`（记录数、通过数、违规数、规则错误数、
契约版本、时间戳），按数据集累积，可按时间窗口查询。

问题率定义（规则错误计入问题，绝不计为通过）：

```
problemRatio = (违规记录数 + 规则错误数) / 校验记录数
```

告警评估（`AlertService`，每次任务完成后自动执行，也可结合指标查询观察）：

1. 取长度 `dq.alert.windowMillis` 的**当前窗口**与紧邻的**基线窗口**，按记录数加权聚合问题率；
   基线窗口没有指标时使用配置的默认基线 `dq.alert.baseline-ratio`；
2. 仅当 `当前问题率 ≥ dq.alert.ratio-threshold` **且**
   `当前问题率 > 基线问题率 × dq.alert.baseline-multiplier` 时才告警，避免绝对阈值虚高和
   基线本身就差时的误报；
3. **去重收敛**：同一数据集已有活动告警且距上次触发不足 `dq.alert.suppress-millis` 时，
   重复抖动只把告警置为 `SUPPRESSED` 并累加 `triggerCount`，不产生新告警；
4. 当前窗口恢复正常后活动告警置为 `RESOLVED`；抑制窗口过后再次异常才产生新告警；
5. 每条告警的 `evidence` 写明窗口长度、样本数、当前问题率、阈值、基线及相对上限，作为触发依据。

告警参数可在 `application.properties` 中覆盖：

| 配置键 | 默认值 | 含义 |
| --- | --- | --- |
| `dq.alert.window-millis` | 60000 | 当前/基线窗口长度 |
| `dq.alert.ratio-threshold` | 0.5 | 当前窗口问题率绝对阈值 |
| `dq.alert.baseline-ratio` | 0.1 | 基线窗口无数据时的默认基线 |
| `dq.alert.baseline-multiplier` | 2.0 | 相对基线的放大倍数 |
| `dq.alert.suppress-millis` | 300000 | 同数据集活动告警的去重抑制窗口 |

## 6. 契约版本与历史可复现

任务只引用提交时的契约版本号与契约快照；契约升级后：

- 新任务默认使用最新版本，也可用 `contractVersion` 指定历史版本；
- 用历史版本契约对同一份数据重放，得到与当时完全一致的结论；
- 指标同时记录 `contractVersion`，可区分不同契约版本下的数据质量。

## 7. HTTP 接口

| 方法与路径 | 说明 |
| --- | --- |
| `POST /api/datasets/{datasetId}` | 注册数据集 |
| `PUT /api/datasets/{datasetId}/contract` | 注册/更新契约（返回新版本号） |
| `GET /api/datasets/{datasetId}/contract?version=` | 查询契约（缺省返回当前版本） |
| `POST /api/datasets/{datasetId}/records` | 接入一批数据（内存模拟） |
| `POST /api/datasets/{datasetId}/tasks?contractVersion=` | 提交校验任务 |
| `GET /api/tasks/{taskId}` | 查询任务状态与校验结论 |
| `GET /api/datasets/{datasetId}/metrics?windowMillis=` | 查询全部/窗口指标 |
| `GET /api/datasets/{datasetId}/alerts` | 查询告警历史 |

请求示例：

```json
// PUT /api/datasets/orders/contract
{
  "fields": [
    {"name": "orderId", "type": "STRING", "required": true, "unique": true},
    {"name": "amount", "type": "DECIMAL", "required": true, "min": 0, "max": 100000},
    {"name": "eventTime", "type": "TIMESTAMP", "maxAgeMillis": 60000}
  ]
}
```

```json
// POST /api/datasets/orders/records
{"records": [{"recordId": "row-1", "fields": {"orderId": "A1", "amount": 500, "eventTime": 1700000000000}}]}
```

## 8. 验证方法

```bash
mvn test
```

共 31 个用例，按业务域组织（测试日志中的 `BUSINESS-COVERAGE` 行会打印每个业务域
覆盖的用例清单及通过/失败统计）：

| 测试类 | 覆盖业务 |
| --- | --- |
| `ContractRegistrationTest` | 畸形/自相矛盾契约拒绝、多处问题一次报全、合法契约接受 |
| `MultiIssueValidationTest` | 五类约束校验、一记录多问题全部报出且可区分、唯一性、根因不重复报 |
| `RuleFailureTest` | 规则抛异常/超时归一化、敏感信息不透出、失败不中断且不计为通过、规则状态隔离 |
| `ConcurrentTaskTest` | 32 任务并发结果一致、指标不丢失、数据快照不被在途改写污染 |
| `AlertDedupTest` | FIRING/SUPPRESSED/RESOLVED、抖动去重、抑制窗口后再次告警、规则错误触发告警 |
| `BaselineComparisonTest` | 绝对阈值与基线倍数双条件判定 |
| `ContractVersioningTest` | 契约版本化、历史版本重放复现结论、非法新版本不污染当前版本 |
| `WebApiIntegrationTest` | HTTP 全链路、错误码映射、畸形 JSON/契约/记录的 400 响应 |
