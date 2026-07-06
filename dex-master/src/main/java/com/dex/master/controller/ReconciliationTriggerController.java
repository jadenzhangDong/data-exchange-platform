package com.dex.master.controller;

import com.dex.master.service.ReconciliationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/master/reconciliation")
public class ReconciliationTriggerController {

    @Autowired
    private ReconciliationService reconciliationService;

    @PostMapping("/run/{configId}")
    public String runReconciliation(@PathVariable String configId) throws Exception {
        reconciliationService.executeReconciliation(configId);
        return "核对任务已启动";
    }

    @PostMapping("/cancel/{jobId}")
    public String cancelJob(@PathVariable String jobId) {
        reconciliationService.cancelJob(jobId);
        return "已取消";
    }

    @PostMapping("/compensate")
    public String compensate(@RequestBody Map<String, Object> params) throws Exception {
        String configId = (String) params.get("configId");
        List<String> pkList = (List<String>) params.get("pkList");
        reconciliationService.compensate(configId, pkList);
        return "补偿完成";
    }
}