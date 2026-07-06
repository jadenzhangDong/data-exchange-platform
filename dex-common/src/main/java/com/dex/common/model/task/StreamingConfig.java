package com.dex.common.model.task;

import lombok.Data;

@Data
public class StreamingConfig {
    private Long checkpointIntervalMs = 60000L;
    private Long maxWaitMs = 5000L;
    private String offsetResetPolicy = "latest";
}