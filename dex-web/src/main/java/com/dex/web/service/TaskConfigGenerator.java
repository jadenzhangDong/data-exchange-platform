package com.dex.web.service;

import com.dex.common.model.metadata.FieldMappingRule;
import com.dex.common.model.metadata.TaskGenerateTemplate;
import com.dex.common.model.task.PluginConfig;
import com.dex.common.model.task.StreamingConfig;
import com.dex.common.model.task.TaskConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TaskConfigGenerator {
    private static final Logger log = LoggerFactory.getLogger(TaskConfigGenerator.class);

    public TaskConfig generate(TaskGenerateTemplate template) {
        log.info("生成任务配置: template={}", template);

        TaskConfig config = new TaskConfig();
        config.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        config.setTaskName(template.getTaskName());
        config.setMode(template.getTaskMode());
        config.setBatchSize(template.getBatchSize() != null ? template.getBatchSize() : 1000);
        config.setCronExpression(template.getCronExpression());

        // 构建 Source 配置（简化：使用 mock 插件）
        PluginConfig source = new PluginConfig();
        source.setType("mock");
        Map<String, Object> sourceParams = new HashMap<>();
        sourceParams.put("dataSourceId", template.getSourceDataSourceId());
        sourceParams.put("tableIds", template.getSelectedTableIds());
        sourceParams.put("selectedFields", template.getSelectedFields());
        source.setParams(sourceParams);
        config.setSource(source);

        // Transform 配置（字段映射）
        List<PluginConfig> transforms = new ArrayList<>();
        if (template.getMappingRules() != null && !template.getMappingRules().isEmpty()) {
            PluginConfig mapper = new PluginConfig();
            mapper.setType("field-mapper");
            Map<String, Object> mapperParams = new HashMap<>();
            List<Map<String, String>> mappings = new ArrayList<>();
            for (FieldMappingRule rule : template.getMappingRules()) {
                Map<String, String> m = new HashMap<>();
                m.put("source", rule.getSourceField());
                m.put("target", rule.getTargetField());
                mappings.add(m);
            }
            mapperParams.put("mapping", mappings);
            mapper.setParams(mapperParams);
            transforms.add(mapper);
        }
        config.setTransforms(transforms);

        // Sink 配置
        PluginConfig sink = new PluginConfig();
        sink.setType("mock");
        Map<String, Object> sinkParams = new HashMap<>();
        sinkParams.put("dataSourceId", template.getSinkDataSourceId());
        sinkParams.put("tableName", template.getTargetTableName());
        sink.setParams(sinkParams);
        config.setSink(sink);

        // 流式配置
        if ("STREAMING".equals(template.getTaskMode())) {
            StreamingConfig streamCfg = new StreamingConfig();
            streamCfg.setCheckpointIntervalMs(60000L);
            streamCfg.setMaxWaitMs(5000L);
            config.setStreamingConfig(streamCfg);
        }

        log.info("任务配置生成完成: taskId={}", config.getTaskId());
        return config;
    }
}