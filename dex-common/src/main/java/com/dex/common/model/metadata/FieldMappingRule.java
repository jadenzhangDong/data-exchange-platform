package com.dex.common.model.metadata;

import lombok.Data;

@Data
public class FieldMappingRule {
    private String sourceField;
    private String sourceTableId;
    private String targetField;
    private String transformScript; // 可选转换表达式
}