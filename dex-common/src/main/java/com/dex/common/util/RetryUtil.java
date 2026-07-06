package com.dex.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.function.Predicate;

public class RetryUtil {
    private static final Logger log = LoggerFactory.getLogger(RetryUtil.class);

    public static <T> T retry(Callable<T> action, int maxRetries, long initialDelayMs, Predicate<Exception> shouldRetry) throws Exception {
        Exception lastException = null;
        long delay = initialDelayMs;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                return action.call();
            } catch (Exception e) {
                lastException = e;
                if (i == maxRetries || !shouldRetry.test(e)) break;
                log.warn("重试 {}/{}: {}", i + 1, maxRetries, e.getMessage());
                Thread.sleep(delay);
                delay *= 2;
            }
        }
        throw lastException != null ? lastException : new RuntimeException("重试失败");
    }

    public static boolean isConnectionError(Throwable e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("Connection") || msg.contains("timeout") || msg.contains("Network"));
    }
}