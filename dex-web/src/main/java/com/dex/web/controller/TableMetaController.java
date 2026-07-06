package com.dex.web.controller;

import com.dex.common.model.entity.TableMetaEntity;
import com.dex.common.repository.TableMetaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meta/table")
public class TableMetaController {

    @Autowired
    private TableMetaRepository tableMetaRepo;

    @GetMapping("/datasource/{dataSourceId}")
    public List<TableMetaEntity> listByDataSource(@PathVariable String dataSourceId) {
        return tableMetaRepo.findByDataSourceId(dataSourceId);
    }

    @GetMapping("/{id}")
    public TableMetaEntity get(@PathVariable String id) {
        return tableMetaRepo.findById(id).orElse(null);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable String id) {
        tableMetaRepo.deleteById(id);
        return "deleted";
    }

    // 探查并保存表结构（可复用 SchemaInspectorService 返回的结果）
}