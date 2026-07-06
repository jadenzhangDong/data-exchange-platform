package com.dex.common.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlertClient {
    private static final Logger log = LoggerFactory.getLogger(AlertClient.class);

    public static void sendAlert(String title, String message) {
        log.warn("【告警】{}: {}", title, message);
        // 扩展：钉钉、邮件、Webhook
        // 例如：DingTalkClient.send(title, message);
    }
}