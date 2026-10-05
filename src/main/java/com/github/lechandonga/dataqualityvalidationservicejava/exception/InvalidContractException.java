package com.github.lechandonga.dataqualityvalidationservicejava.exception;

import com.github.lechandonga.dataqualityvalidationservicejava.model.ErrorCode;

import java.util.List;

/**
 * 非法或自相矛盾的契约异常：拒绝注册并附带全部矛盾点，绝不静默接受。
 */
public class InvalidContractException extends ServiceException {
    public InvalidContractException(List<String> reasons) {
        super(ErrorCode.INVALID_CONTRACT,
                "契约非法，已拒绝注册（共 " + reasons.size() + " 处问题）", reasons);
    }
}
