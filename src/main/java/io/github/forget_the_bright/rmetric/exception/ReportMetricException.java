package io.github.forget_the_bright.rmetric.exception;

import cn.hutool.core.util.StrUtil;

import java.util.function.Supplier;

/**
 * TODO
 *
 * @author wanghao(helloworlwh@163.com)
 * @since 2026/6/1 15:29
 */
public class ReportMetricException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * 返回给前端的错误code
     */
    private int errCode = 500;

    public ReportMetricException(String message) {
        super(message);
    }

    public ReportMetricException(String message, int errCode) {
        super(message);
        this.errCode = errCode;
    }

    public static void throwByFlag(String message, Boolean flag) {
        if (flag) {
            throw new ReportMetricException(message);
        }
    }

    public static void throwByFlag(Boolean flag, String template, Object... value) {
        if (flag) {
            throw new ReportMetricException(StrUtil.format(template, value));
        }
    }


    public static void throwByFlag(Boolean flag, String template, Supplier<Object>... value) {
        if (flag) {
            throw new ReportMetricException(StrUtil.format(template, value));
        }
    }

    public int getErrCode() {
        return errCode;
    }

    public ReportMetricException(Throwable cause) {
        super(cause);
    }

    public ReportMetricException(String message, Throwable cause) {
        super(message, cause);
    }
}
