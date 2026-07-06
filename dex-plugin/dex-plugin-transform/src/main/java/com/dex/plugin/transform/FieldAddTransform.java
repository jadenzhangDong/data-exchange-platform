package com.dex.plugin.transform;

import com.dex.plugin.api.Transform;
import java.util.Map;

public class FieldAddTransform implements Transform<Map<String, Object>, Map<String, Object>> {
    private String fieldName;
    private Object fieldValue;

    @Override
    public void open(Map<String, Object> config) {
        this.fieldName = (String) config.get("field");
        this.fieldValue = config.get("value");
        if (fieldName == null) {
            throw new IllegalArgumentException("field 参数必须提供");
        }
    }

    @Override
    public Map<String, Object> transform(Map<String, Object> input) {
        input.put(fieldName, fieldValue);
        return input;
    }
}