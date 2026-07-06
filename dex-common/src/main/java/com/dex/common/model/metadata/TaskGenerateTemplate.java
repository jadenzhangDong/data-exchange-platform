package com.dex.common.model.metadata;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class TaskGenerateTemplate {
    private String sourceDataSourceId;
    private List<String> selectedTableIds;
    private Map<String, List<String>> selectedFields;
    private String sinkDataSourceId;
    private String targetTableName;
    private List<FieldMappingRule> mappingRules;
    private String taskMode;
    private String cronExpression;
    private Integer batchSize;
    private String taskName;
    private String taskDescription;
    private String assignedWorkerId;
}