package com.lab.common.exception;

/**
 * 参数校验异常：{@link ErrorCode#PARAM_INVALID} 的专用子类。
 *
 * <p>单独成类是为了让测试可以精确断言"是校验拦住的"，
 * 而不是把所有失败都归到一个笼统的 BusinessException 上。</p>
 */
public class ValidationException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String detail) {
        super(ErrorCode.PARAM_INVALID, detail);
    }
}
