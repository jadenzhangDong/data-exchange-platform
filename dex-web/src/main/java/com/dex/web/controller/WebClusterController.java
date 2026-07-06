package com.dex.web.controller;

import com.dex.common.model.WorkerInfo;
import com.dex.web.discovery.MasterDiscovery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api/web/cluster")
public class WebClusterController {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private MasterDiscovery masterDiscovery;

    @GetMapping("/workers")
    public List<WorkerInfo> listWorkers() {
        try {
            String masterUrl = masterDiscovery.getActiveMasterAddress() + "/api/master/workers";
            WorkerInfo[] workers = restTemplate.getForObject(masterUrl, WorkerInfo[].class);
            return workers != null ? Arrays.asList(workers) : Collections.emptyList();
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    @GetMapping("/master")
    public Map<String, String> getMasterStatus() {
        Map<String, String> result = new HashMap<>();
        try {
            String masterUrl = masterDiscovery.getActiveMasterAddress() + "/api/master/status";
            String status = restTemplate.getForObject(masterUrl, String.class);
            result.put("status", status);
            result.put("activeAddress", masterDiscovery.getActiveMasterAddress());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("activeAddress", "unknown");
        }
        return result;
    }
}