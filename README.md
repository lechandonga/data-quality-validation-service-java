# 数据质量校验与监控服务（DQVS）

基于 Spring Boot 3.4 / Java 21 的全内存服务，用内存态数据集模拟接入方数据，覆盖：
契约注册与校验、校验任务并发提交、规则异常隔离、指标累积、基线告警与去重、历史结论复现。

## 1. 契约模型

契约按数据集注册，**只增不改**，每次发布生成不可变新版本（`version` 从 1 递增）与内容
SHA-256 `checksum`。字段契约 `FieldSpec`：

| 字段 | 说明 |
| --- | --- |
| `name` / `type` | 字段名；类型限定 `STRING/INTEGER/DECIMAL/BOOLEAN/TIMESTAMP` |
| `required` | 必填，空串视为缺失 |
| `min` / `max` | 取值区间，只允许声明在 `INTEGER/DECIMAL` 上；`min > max` 直接拒绝 |
| `unique` | 数据集内唯一，重复值的每条记录都会被标记 |
| `freshnessCheck` + `maxAgeMillis` | 时效约束，只允许声明在 `TIMESTAMP` 上，要求正的 `maxAgeMillis` |

`TIMESTAMP` 接受 epoch 毫秒数字或 ISO-8601 字符串。

### 非法/自相矛盾契约

注册时**一次性收集全部问题**后返回 `400 MALFORMED_CONTRACT`，`details` 中逐条说明，
绝不静默接受任何一项，包括：空字段列表、字段重名、未知类型、`min/max` 用错类型、
`min > max`、时效开关用错类型、开启时效却不给正的 `maxAgeMillis`、未开时效却配置
`maxAgeMillis`（存在被静默忽略的歧义）、与当前版本完全相同的重复注册。

## 2. 校验执行与问题分类

内置五条字段级规则（见 `engine/BuiltInRules`），规则可通过 `Rule` 接口扩展：

| 规则码 | 问题码 `IssueCode` | 含义 |
| --- | --- | --- |
| `REQUIRED` | `REQUIRED_MISSING` | 必填缺失 |
| `FIELD_TYPE` | `FIELD_TYPE_MISMATCH` | 类型不匹配 |
| `VALUE_RANGE` | `VALUE_OUT_OF_RANGE` | 超出 `[min,max]` |
| `UNIQUENESS` | `NOT_UNIQUE` | 数据集内不唯一 |
| `FRESHNESS` | `FRESHNESS_VIOLATION` | 超过最大延迟 |

一条记录命中多个问题时**全部保留**，每条带独立 `code/field/message`，原因可区分；
必填缺失后跳过依赖该值的规则，避免误导性噪音。

### 规则异常归一化

规则内部抛出的任何异常都在引擎内被捕获：

- 不中断同字段其它规则、不中断同批次其它记录；
- 记为一条 `RULE_EVALUATION_ERROR`，该记录判为**失败**，绝不当成通过；
- 原始异常类与堆栈**不透传**给调用方，仅写服务端日志，对外给出归一化 `errorType`：
  `RULE_DEFECT`（NPE 等规则缺陷）、`RULE_CONFIG_ERROR`、`RULE_COMPUTATION_ERROR`、
  `RULE_DATA_COERCION_ERROR`、`RULE_UNSUPPORTED`、兜底 `RULE_INTERNAL_ERROR`。

## 3. 任务并发与隔离

`POST /api/tasks?datasetId=...` 异步提交（状态 `PENDING/RUNNING/COMPLETED`）。
提交瞬间在数据集锁内固化契约版本 + checksum + 记录深拷贝快照，因此：

- 同一数据集并发提交的多个任务互不污染；
- 任务执行期间替换数据集数据，在途任务仍以提交时快照为准，新任务才看到新数据；
- 单条规则失败不会导致任务失败，批次跑完即为 `COMPLETED`。

## 4. 指标、告警与去重

- 每个任务完成即把 `ValidationReport` 追加到指标存储；
  `GET /api/metrics?datasetId=&from=&to=&contractVersion=` 按时间窗口聚合
  （任务数、记录数、通过/失败数、规则异常数、通过率、规则异常率、关联任务）。
- 告警以当前窗口与**等长基线窗口**对比（默认窗口 5 分钟，可配）：
  - `PASS_RATE_DROP`：通过率下降超过 `dqvs.alert.pass-rate-drop-threshold`（默认 0.10）；
  - `RULE_ERROR_SPIKE`：规则异常率上升超过 `dqvs.alert.rule-error-spike-threshold`（默认 0.05）。
- 每条告警的 `evidence` 给出窗口边界、基线值、当前值、差值、阈值与关联任务 ID，触发依据可解释。
- **抖动收敛**：同一 `dedupeKey = datasetId:alertType` 在抑制窗口
  （`dqvs.alert.dedupe-window-millis`，默认 5 分钟）内重复触发不产生新告警，
  只累加 `suppressedCount`；数据恢复后旧告警失活，再次劣化可重新告警。

## 5. 历史可复现

任务永久绑定提交时的契约版本与 checksum；历史契约版本只增不改。
`POST /api/tasks/{taskId}/replay` 用历史版本契约 + 任务快照重新执行，
对比与时间/任务 ID 无关的问题摘要，返回 `identical`；指标支持按 `contractVersion`
分别查询，契约升版后旧结论仍可独立复核。

## 6. HTTP 接口

| 方法与路径 | 用途 |
| --- | --- |
| `POST /api/contracts` | 注册契约（非法返回 400 + 全部明细） |
| `GET /api/contracts/{datasetId}` | 当前契约与全部版本 |
| `GET /api/contracts/{datasetId}/versions/{v}` | 指定历史版本 |
| `PUT /api/datasets/{datasetId}/records` | 整体替换内存数据（深拷贝） |
| `GET /api/datasets/{datasetId}` | 查看当前数据 |
| `POST /api/tasks?datasetId=...` | 提交校验任务 |
| `GET /api/tasks/{taskId}` | 查询任务状态与完整报告 |
| `POST /api/tasks/{taskId}/replay` | 用历史契约重放并比对结论 |
| `GET /api/metrics?datasetId=&from=&to=&contractVersion=` | 窗口指标 |
| `POST /api/alerts/evaluate?datasetId=&windowMillis=` | 手动触发一次窗口评估 |
| `GET /api/alerts?datasetId=...` | 告警历史（含失活与抑制计数） |

统一错误响应：`{timestamp, errorCode, message, details}`，分类码见 `domain/ErrorCode`；
任意未预期异常返回脱敏的 `INTERNAL_ERROR`，堆栈仅在服务端日志。

## 7. 验证方法

```bash
mvn test
```

测试日志通过 JUnit5 扩展 `BusinessCoverageLogger` 输出「业务模块 → 用例 → 结果」，
关键场景与对应测试类：

| 业务场景 | 测试类 |
| --- | --- |
| 畸形/自相矛盾契约一次性拒绝、版本演进、checksum | `ContractRegistrationTest` |
| 多问题同时报出、必填/类型/区间/唯一性/时效 | `ValidationEngineTest` |
| 规则抛异常归一化、不中断批次、失败不算通过 | `ValidationEngineTest` |
| 同数据集 12 任务并发提交、快照隔离、指标无丢失 | `ConcurrentTaskTest` |
| 通过率骤降告警、evidence、抖动去重、恢复失活、跨抑制窗再告警 | `AlertAndReproducibilityTest` |
| 契约升版后历史任务重放一致、指标按版本过滤 | `AlertAndReproducibilityTest` |
| 端到端 HTTP 链路、400/404 分类、错误响应无堆栈、坏 JSON 处理 | `WebApiIntegrationTest` |

快速体验（启动后自动种入 `demo-orders` 数据集与契约）：

```bash
mvn spring-boot:run
curl -X POST 'http://localhost:8080/api/tasks?datasetId=demo-orders'
curl 'http://localhost:8080/api/tasks/<taskId>'
```
