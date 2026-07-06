package com.dex.plugin.transform;

import com.dex.plugin.api.Transform;
import java.util.List;
import java.util.Map;

public class FieldRemoveTransform implements Transform<Map<String, Object>, Map<String, Object>> {
    private List<String> fields;

    @Override
    public void open(Map<String, Object> config) {
        this.fields = (List<String>) config.get("fields");
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("fields 参数必须提供");
        }
    }

    @Override
    public Map<String, Object> transform(Map<String, Object> input) {
        for (String field : fields) {
            input.remove(field);
        }
        return input;
    }
}