package com.dex.common.model.metadata;

import lombok.Data;
import java.util.List;

@Data
public class TableMeta {
    private String id;
    private String dataSourceId;
    private String schemaName;
    private String tableName;
    private String tableType; // TABLE, VIEW, TOPIC, FILE
    private List<ColumnMeta> columns;
    private Long rowCount;
}