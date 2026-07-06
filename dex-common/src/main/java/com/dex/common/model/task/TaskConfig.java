package com.dex.common.model.task;

import lombok.Data;
import java.util.List;

@Data
public class TaskConfig {
    private String taskId;
    private String taskName;
    // 只有两种模式：BATCH, STREAMING
    private String mode; // BATCH, STREAMING

    // 定时配置（仅 BATCH 模式有效）
    private String cronExpression; // 如果为空，表示手动触发
    private Boolean scheduled; // true: 定时触发, false: 手动触发

    private Integer batchSize = 1000;
    private Integer parallelism = 1;
    private Long timeoutMs = 3600000L;
    private PluginConfig source;
    private List<PluginConfig> transforms;
    private PluginConfig sink;
    private StreamingConfig streamingConfig;
}