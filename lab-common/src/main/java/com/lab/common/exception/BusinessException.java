package com.lab.common.exception;

/**
 * 业务异常：携带 {@link ErrorCode}，用于表达"规则不满足"而非"程序出错"。
 *
 * <p>继承 RuntimeException，使调用方不必在签名上层层声明，
 * 同时保证在会话模板里能被 {@code catch (RuntimeException)} 捕获并触发回滚。</p>
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + "：" + detail);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, "无补充信息");
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
