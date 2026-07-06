package com.dex.plugin.transform;

import com.dex.plugin.api.Transform;
import java.text.SimpleDateFormat;
import java.util.Map;

public class TypeConverterTransform implements Transform<Map<String, Object>, Map<String, Object>> {
    private String field;
    private String targetType;
    private String format;

    @Override
    public void open(Map<String, Object> config) {
        this.field = (String) config.get("field");
        this.targetType = (String) config.get("targetType");
        this.format = (String) config.get("format");
        if (field == null || targetType == null) {
            throw new IllegalArgumentException("field 和 targetType 参数必须提供");
        }
    }

    @Override
    public Map<String, Object> transform(Map<String, Object> input) {
        Object val = input.get(field);
        if (val == null) return input;

        Object converted = null;
        try {
            String strVal = val.toString();
            switch (targetType.toLowerCase()) {
                case "int":
                case "integer":
                    converted = Integer.parseInt(strVal);
                    break;
                case "long":
                    converted = Long.parseLong(strVal);
                    break;
                case "double":
                    converted = Double.parseDouble(strVal);
                    break;
                case "boolean":
                    converted = Boolean.parseBoolean(strVal);
                    break;
                case "date":
                    if (format != null && !format.isEmpty()) {
                        SimpleDateFormat sdf = new SimpleDateFormat(format);
                        converted = sdf.parse(strVal);
                    } else {
                        converted = strVal; // 保持字符串
                    }
                    break;
                case "string":
                default:
                    converted = strVal;
            }
        } catch (Exception e) {
            // 转换失败，保留原值
            return input;
        }
        input.put(field, converted);
        return input;
    }
}