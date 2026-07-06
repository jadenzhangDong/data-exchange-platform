package com.dex.common.model.task;

import lombok.Data;
import java.util.Map;

@Data
public class PluginConfig {
    private String type;
    private Map<String, Object> params;
}