package com.dex.plugin.transform;

import com.dex.plugin.api.Transform;
import java.util.Map;

public class FilterTransform implements Transform<Map<String, Object>, Map<String, Object>> {
    private String field;
    private String operator;
    private Object value;

    @Override
    public void open(Map<String, Object> config) {
        this.field = (String) config.get("field");
        this.operator = (String) config.get("op");
        this.value = config.get("value");
        if (field == null || operator == null) {
            throw new IllegalArgumentException("field 和 op 参数必须提供");
        }
    }

    @Override
    public Map<String, Object> transform(Map<String, Object> input) {
        Object val = input.get(field);
        if (val == null) return null;

        switch (operator) {
            case "==":
                return val.equals(value) ? input : null;
            case "!=":
                return !val.equals(value) ? input : null;
            case ">":
                return compare(val, value) > 0 ? input : null;
            case ">=":
                return compare(val, value) >= 0 ? input : null;
            case "<":
                return compare(val, value) < 0 ? input : null;
            case "<=":
                return compare(val, value) <= 0 ? input : null;
            default:
                throw new IllegalArgumentException("不支持的运算符: " + operator);
        }
    }

    @SuppressWarnings("unchecked")
    private int compare(Object a, Object b) {
        if (a instanceof Comparable && b instanceof Comparable) {
            return ((Comparable) a).compareTo(b);
        }
        return String.valueOf(a).compareTo(String.valueOf(b));
    }
}