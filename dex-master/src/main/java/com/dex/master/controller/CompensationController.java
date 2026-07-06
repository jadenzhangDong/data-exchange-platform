package com.dex.master.controller;

import com.dex.master.service.CompensationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/master/compensation")
public class CompensationController {

    @Autowired
    private CompensationService compensationService;

    @PostMapping("/single")
    public String compensateSingle(@RequestBody Map<String, Object> params) throws Exception {
        Long diffId = Long.valueOf(params.get("diffId").toString());
        String action = (String) params.getOrDefault("action", "AUTO");
        String compensatedBy = (String) params.getOrDefault("compensatedBy", "system");
        compensationService.compensateSingle(diffId, action, compensatedBy);
        return "补偿成功";
    }

    @PostMapping("/batch")
    public String compensateBatch(@RequestBody Map<String, Object> params) throws Exception {
        String configId = (String) params.get("configId");
        String diffType = (String) params.get("diffType");
        String compensatedBy = (String) params.getOrDefault("compensatedBy", "system");
        compensationService.compensateBatch(configId, diffType, compensatedBy);
        return "批量补偿完成";
    }
}