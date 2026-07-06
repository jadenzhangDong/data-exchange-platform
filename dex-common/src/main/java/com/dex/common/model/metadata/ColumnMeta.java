package com.dex.common.model.metadata;

import lombok.Data;

@Data
public class ColumnMeta {
    private String columnName;
    private String dataType;
    private Integer columnLength;
    private Boolean isNullable;
    private Boolean isPrimaryKey;
    private String comment;
    private String defaultValue;
}