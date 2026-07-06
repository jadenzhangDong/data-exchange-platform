package com.dex.web.controller;

import com.dex.common.model.entity.FieldMappingRuleEntity;
import com.dex.common.repository.FieldMappingRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/meta/mapping")
public class FieldMappingRuleController {

    @Autowired
    private FieldMappingRuleRepository mappingRepo;

    @GetMapping
    public List<FieldMappingRuleEntity> list() {
        return mappingRepo.findAll();
    }

    @PostMapping
    public FieldMappingRuleEntity create(@RequestBody FieldMappingRuleEntity entity) {
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        return mappingRepo.save(entity);
    }

    @PutMapping
    public FieldMappingRuleEntity update(@RequestBody FieldMappingRuleEntity entity) {
        entity.setUpdateTime(new Date());
        return mappingRepo.save(entity);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable String id) {
        mappingRepo.deleteById(id);
        return "deleted";
    }
}