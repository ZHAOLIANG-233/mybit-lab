package com.lab.common.exception;

/**
 * 统一错误码。
 *
 * <p>错误码集中在一个枚举里，避免"同一个业务失败在不同层返回不同数字"，
 * 也便于前端或调用方按码分支处理。</p>
 */
public enum ErrorCode {

    /** 参数校验不通过（缺少必填项、取值越界、类型不合法等） */
    PARAM_INVALID(40001, "参数校验失败"),

    /** 业务规则不允许当前操作（例如重复工号、状态已变更） */
    BUSINESS_REJECTED(40901, "业务规则拒绝该操作"),

    /** 数据不存在 */
    DATA_NOT_FOUND(40401, "目标数据不存在"),

    /** 数据访问层异常（SQL 语法、连接、超时等） */
    DATA_ACCESS_ERROR(50001, "数据访问异常"),

    /** 配置缺失或非法（例如数据库口令未配置） */
    CONFIG_INVALID(50002, "配置缺失或非法");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
