package com.dex.web.controller;

import com.dex.common.model.entity.DataSourceMetaEntity;
import com.dex.common.model.entity.TableMetaEntity;
import com.dex.common.model.metadata.TableMeta;
import com.dex.common.model.metadata.TaskGenerateTemplate;
import com.dex.common.model.task.TaskConfig;
import com.dex.common.repository.DataSourceMetaRepository;
import com.dex.common.repository.TableMetaRepository;
import com.dex.common.util.JsonUtil;
import com.dex.web.service.SchemaInspectorService;
import com.dex.web.service.TaskConfigGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/meta")
public class MetadataController {

    private static final Logger log = LoggerFactory.getLogger(MetadataController.class);

    @Autowired
    private DataSourceMetaRepository dsRepo;

    @Autowired
    private TableMetaRepository tableMetaRepo;

    @Autowired
    private SchemaInspectorService inspectorService;

    @Autowired
    private TaskConfigGenerator taskConfigGenerator;

    // ========== 数据源管理 ==========

    @GetMapping("/datasource")
    public List<DataSourceMetaEntity> listDataSources() {
        return dsRepo.findAll();
    }

    @GetMapping("/datasource/{id}")
    public DataSourceMetaEntity getDataSource(@PathVariable String id) {
        return dsRepo.findById(id).orElse(null);
    }

    @PostMapping("/datasource")
    public DataSourceMetaEntity createDataSource(@RequestBody DataSourceMetaEntity meta) {
        meta.setId(UUID.randomUUID().toString().replace("-", ""));
        meta.setCreateTime(new Date());
        meta.setUpdateTime(new Date());
        return dsRepo.save(meta);
    }

    @PutMapping("/datasource")
    public DataSourceMetaEntity updateDataSource(@RequestBody DataSourceMetaEntity meta) {
        meta.setUpdateTime(new Date());
        return dsRepo.save(meta);
    }

    @DeleteMapping("/datasource/{id}")
    public String deleteDataSource(@PathVariable String id) {
        dsRepo.deleteById(id);
        return "deleted";
    }

    @PostMapping("/datasource/{id}/test")
    public String testConnection(@PathVariable String id) {
        return "Connection OK";
    }

    @GetMapping("/datasource/{id}/inspect")
    public List<TableMeta> inspectSchema(@PathVariable String id) {
        DataSourceMetaEntity meta = dsRepo.findById(id).orElse(null);
        if (meta == null) {
            throw new RuntimeException("数据源不存在");
        }
        Map<String, Object> configMap = JsonUtil.fromJson(meta.getConfig(), Map.class);
        return inspectorService.inspect(id, configMap);
    }

    @PostMapping("/datasource/{id}/inspect-and-save")
    public ResponseEntity<?> inspectAndSave(@PathVariable String id) {
        try {
            DataSourceMetaEntity meta = dsRepo.findById(id).orElse(null);
            if (meta == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("数据源不存在");
            }

            Map<String, Object> configMap;
            try {
                configMap = JsonUtil.fromJson(meta.getConfig(), Map.class);
            } catch (Exception e) {
                log.error("解析数据源配置失败", e);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("数据源配置 JSON 格式错误: " + e.getMessage());
            }

            List<TableMeta> tables = inspectorService.inspect(id, configMap);

            // 先删除已有的表元数据（避免重复）
            List<TableMetaEntity> existing = tableMetaRepo.findByDataSourceId(id);
            if (!existing.isEmpty()) {
                tableMetaRepo.deleteAll(existing);
            }

            // 保存新的
            List<TableMetaEntity> saved = new ArrayList<>();
            for (TableMeta t : tables) {
                TableMetaEntity entity = new TableMetaEntity();
                entity.setId(UUID.randomUUID().toString().replace("-", ""));
                entity.setDataSourceId(id);
                entity.setSchemaName(t.getSchemaName());
                entity.setTableName(t.getTableName());
                entity.setTableType(t.getTableType());
                entity.setColumns(JsonUtil.toJson(t.getColumns()));
                entity.setRowCount(t.getRowCount());
                entity.setCreateTime(new Date());
                entity.setUpdateTime(new Date());
                saved.add(tableMetaRepo.save(entity));
            }

            log.info("探查并保存成功，数据源ID: {}, 表数量: {}", id, saved.size());
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            log.error("探查并保存表结构失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("探查失败: " + e.getMessage());
        }
    }

    @PostMapping("/task/generate")
    public TaskConfig generateTask(@RequestBody TaskGenerateTemplate template) {
        return taskConfigGenerator.generate(template);
    }
}