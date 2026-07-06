package com.dex.web.controller;

import com.dex.common.model.entity.*;
import com.dex.common.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {

    @Autowired
    private ReconciliationConfigRepository configRepo;
    @Autowired
    private ReconciliationJobRepository jobRepo;
    @Autowired
    private ReconciliationDiffRepository diffRepo;

    @GetMapping("/config")
    public List<ReconciliationConfigEntity> listConfigs() {
        return configRepo.findAll();
    }

    @PostMapping("/config")
    public ReconciliationConfigEntity createConfig(@RequestBody ReconciliationConfigEntity config) {
        config.setId(UUID.randomUUID().toString().replace("-", ""));
        config.setCreateTime(new Date());
        config.setUpdateTime(new Date());
        return configRepo.save(config);
    }

    @PutMapping("/config")
    public ReconciliationConfigEntity updateConfig(@RequestBody ReconciliationConfigEntity config) {
        config.setUpdateTime(new Date());
        return configRepo.save(config);
    }

    @DeleteMapping("/config/{id}")
    public String deleteConfig(@PathVariable String id) {
        configRepo.deleteById(id);
        return "deleted";
    }

    // 触发核对（调用 Master）
    @PostMapping("/run/{configId}")
    public String runReconciliation(@PathVariable String configId) {
        String masterUrl = "http://localhost:8080/api/master/reconciliation/run/" + configId;
        new RestTemplate().postForObject(masterUrl, null, String.class);
        return "核对任务已启动";
    }

    @PostMapping("/cancel/{jobId}")
    public String cancelJob(@PathVariable String jobId) {
        String masterUrl = "http://localhost:8080/api/master/reconciliation/cancel/" + jobId;
        new RestTemplate().postForObject(masterUrl, null, String.class);
        return "已取消";
    }

    @GetMapping("/job/{configId}")
    public List<ReconciliationJobEntity> listJobs(@PathVariable String configId) {
        return jobRepo.findByConfigId(configId);
    }

    @GetMapping("/diff/{jobId}")
    public List<ReconciliationDiffEntity> listDiffs(@PathVariable String jobId,
                                                    @RequestParam(required = false) String diffType,
                                                    @RequestParam(required = false) String status) {
        if (diffType != null && status != null) {
            // 应该用 jobId 查询，而不是 configId
            return diffRepo.findByJobIdAndDiffTypeAndStatus(jobId, diffType, status);
        } else if (diffType != null) {
            return diffRepo.findByJobIdAndDiffType(jobId, diffType);
        }
        return diffRepo.findByJobId(jobId);
    }

    @GetMapping("/diff/config/{configId}")
    public List<ReconciliationDiffEntity> listDiffsByConfig(@PathVariable String configId,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String diffType) {
        if (status != null && diffType != null) {
            return diffRepo.findByConfigIdAndStatusAndDiffType(configId, status, diffType);
        } else if (status != null) {
            return diffRepo.findByConfigIdAndStatus(configId, status);
        } else {
            return diffRepo.findByConfigId(configId);
        }
    }

    @PostMapping("/diff/fix/{diffId}")
    public String fixDiff(@PathVariable Long diffId) {
        ReconciliationDiffEntity diff = diffRepo.findById(diffId).orElse(null);
        if (diff != null) {
            diff.setStatus("FIXED");
            diff.setFixedTime(new Date());
            diffRepo.save(diff);
        }
        return "fixed";
    }

    @PostMapping("/diff/ignore/{diffId}")
    public String ignoreDiff(@PathVariable Long diffId) {
        ReconciliationDiffEntity diff = diffRepo.findById(diffId).orElse(null);
        if (diff != null) {
            diff.setStatus("IGNORED");
            diff.setFixedTime(new Date());
            diffRepo.save(diff);
        }
        return "ignored";
    }

    @PostMapping("/compensate")
    public String compensate(@RequestBody Map<String, Object> params) {
        String masterUrl = "http://localhost:8080/api/master/reconciliation/compensate";
        new RestTemplate().postForObject(masterUrl, params, String.class);
        return "补偿任务已提交";
    }
}