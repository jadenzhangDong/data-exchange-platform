package com.dex.web.service;

import com.dex.common.model.entity.*;
import com.dex.common.model.task.PluginConfig;
import com.dex.common.model.task.TaskConfig;
import com.dex.common.repository.*;
import com.dex.common.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TaskGeneratorService {

    @Autowired
    private TableMetaRepository tableMetaRepo;

    @Autowired
    private FieldMappingRuleRepository mappingRuleRepo;

    @Autowired
    private TaskTemplateRepository templateRepo;

    public TaskConfig generateTask(String templateId,
                                   String sourceTableId,
                                   String targetTableId,
                                   String mappingRuleId,
                                   Map<String, Object> overrides) throws Exception {

        TaskTemplateEntity template = templateRepo.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在"));

        TableMetaEntity sourceTable = tableMetaRepo.findById(sourceTableId)
                .orElseThrow(() -> new IllegalArgumentException("源表不存在"));

        // 目标表允许不存在（用户自定义表名）
        TableMetaEntity targetTable = null;
        if (targetTableId != null && !targetTableId.isEmpty()) {
            targetTable = tableMetaRepo.findById(targetTableId).orElse(null);
        }

        // 获取映射规则
        FieldMappingRuleEntity mappingRule = null;
        if (mappingRuleId != null && !mappingRuleId.isEmpty()) {
            mappingRule = mappingRuleRepo.findById(mappingRuleId).orElse(null);
        }

        // 从 overrides 中获取目标表名（用户输入）
        String targetTableName = (String) overrides.getOrDefault("targetTableName", "target_table");
        if (targetTable != null) {
            targetTableName = targetTable.getTableName();
        }

        // 解析模板
        Map<String, Object> sourceTemplate = JsonUtil.fromJson(template.getSourceTemplate(), Map.class);
        Map<String, Object> sinkTemplate = JsonUtil.fromJson(template.getSinkTemplate(), Map.class);
        List<Map<String, Object>> transformTemplates = null;
        if (template.getTransformTemplates() != null && !template.getTransformTemplates().isEmpty()) {
            transformTemplates = JsonUtil.fromJson(template.getTransformTemplates(), List.class);
        }

        // 替换占位符
        Map<String, Object> sourceConfig = fillPlaceholders(sourceTemplate, sourceTable, targetTable, targetTableName, mappingRule);
        Map<String, Object> sinkConfig = fillPlaceholders(sinkTemplate, sourceTable, targetTable, targetTableName, mappingRule);
        List<Map<String, Object>> transforms = new ArrayList<>();
        if (transformTemplates != null) {
            for (Map<String, Object> tmpl : transformTemplates) {
                transforms.add(fillPlaceholders(tmpl, sourceTable, targetTable, targetTableName, mappingRule));
            }
        }

        // 构建 TaskConfig
        TaskConfig config = new TaskConfig();

        // 🔧 修复：生成恰好 32 字符的 taskId（无前缀，纯 UUID 去连字符）
        String uuid = UUID.randomUUID().toString().replace("-", "");
        config.setTaskId(uuid);  // 正好 32 字符

        config.setTaskName(overrides.containsKey("taskName") ? (String) overrides.get("taskName") :
                template.getName() + "-" + sourceTable.getTableName());
        config.setMode(template.getMode());
        config.setBatchSize(overrides.containsKey("batchSize") ? (Integer) overrides.get("batchSize") : template.getDefaultBatchSize());
        config.setCronExpression(overrides.containsKey("cron") ? (String) overrides.get("cron") : template.getDefaultCron());

        // 转换 Source/Sink/Transform
        config.setSource(JsonUtil.fromJson(JsonUtil.toJson(sourceConfig), PluginConfig.class));
        config.setSink(JsonUtil.fromJson(JsonUtil.toJson(sinkConfig), PluginConfig.class));
        if (!transforms.isEmpty()) {
            List<PluginConfig> transformConfigs = new ArrayList<>();
            for (Map<String, Object> t : transforms) {
                transformConfigs.add(JsonUtil.fromJson(JsonUtil.toJson(t), PluginConfig.class));
            }
            config.setTransforms(transformConfigs);
        }

        return config;
    }

    private Map<String, Object> fillPlaceholders(Map<String, Object> template,
                                                 TableMetaEntity sourceTable,
                                                 TableMetaEntity targetTable,
                                                 String targetTableName,
                                                 FieldMappingRuleEntity mappingRule) {
        // 深拷贝
        Map<String, Object> result = new LinkedHashMap<>(template);
        replacePlaceholder(result, "{{sourceTable}}", sourceTable.getTableName());
        replacePlaceholder(result, "{{targetTable}}", targetTableName);
        replacePlaceholder(result, "{{sourceSchema}}", sourceTable.getSchemaName());
        if (mappingRule != null) {
            replacePlaceholder(result, "{{mapping}}", mappingRule.getMappingJson());
        }
        return result;
    }

    private void replacePlaceholder(Map<String, Object> map, String key, Object value) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof String && entry.getValue().equals(key)) {
                entry.setValue(value);
            } else if (entry.getValue() instanceof Map) {
                replacePlaceholder((Map<String, Object>) entry.getValue(), key, value);
            } else if (entry.getValue() instanceof List) {
                List<?> list = (List<?>) entry.getValue();
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Map) {
                        replacePlaceholder((Map<String, Object>) item, key, value);
                    }
                }
            }
        }
    }
}