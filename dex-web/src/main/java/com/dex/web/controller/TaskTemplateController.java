package com.dex.web.controller;

import com.dex.common.model.entity.TaskTemplateEntity;
import com.dex.common.repository.TaskTemplateRepository;
import com.dex.common.model.task.TaskConfig;
import com.dex.web.service.TaskGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/meta/template")
public class TaskTemplateController {

    @Autowired
    private TaskTemplateRepository templateRepo;

    @Autowired
    private TaskGeneratorService taskGeneratorService;

    @GetMapping
    public List<TaskTemplateEntity> list() {
        return templateRepo.findAll();
    }

    @PostMapping
    public TaskTemplateEntity create(@RequestBody TaskTemplateEntity entity) {
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        return templateRepo.save(entity);
    }

    @PutMapping
    public TaskTemplateEntity update(@RequestBody TaskTemplateEntity entity) {
        entity.setUpdateTime(new Date());
        return templateRepo.save(entity);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable String id) {
        templateRepo.deleteById(id);
        return "deleted";
    }

    @PostMapping("/generate")
    public Map<String, Object> generateTask(@RequestBody Map<String, String> params) throws Exception {
        String templateId = params.get("templateId");
        String sourceTableId = params.get("sourceTableId");
        String targetTableId = params.get("targetTableId");
        String mappingRuleId = params.get("mappingRuleId");
        String targetTableName = params.get("targetTableName");  // 新增

        Map<String, Object> overrides = new HashMap<>();
        if (params.containsKey("batchSize")) {
            overrides.put("batchSize", Integer.parseInt(params.get("batchSize")));
        }
        if (params.containsKey("cron")) {
            overrides.put("cron", params.get("cron"));
        }
        if (targetTableName != null && !targetTableName.isEmpty()) {
            overrides.put("targetTableName", targetTableName);
        }

        TaskConfig config = taskGeneratorService.generateTask(templateId, sourceTableId, targetTableId, mappingRuleId, overrides);
        Map<String, Object> response = new HashMap<>();
        response.put("taskConfig", config);
        return response;
    }
}