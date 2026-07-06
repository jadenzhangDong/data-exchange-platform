package com.dex.plugin.transform;

import com.dex.plugin.api.Transform;
import java.util.Map;

public class FieldMapperTransform implements Transform<Map<String, Object>, Map<String, Object>> {
    private Map<String, String> mapping;

    @Override
    public void open(Map<String, Object> config) {
        mapping = (Map<String, String>) config.get("mapping");
        if (mapping == null) {
            throw new IllegalArgumentException("mapping 参数必须提供");
        }
    }

    @Override
    public Map<String, Object> transform(Map<String, Object> input) {
        Map<String, Object> output = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String targetKey = mapping.getOrDefault(entry.getKey(), entry.getKey());
            output.put(targetKey, entry.getValue());
        }
        return output;
    }
}