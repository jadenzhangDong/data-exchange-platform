package com.dex.master.controller;

import com.dex.common.model.WorkerInfo;
import com.dex.master.elector.LeaderElector;
import com.dex.master.listener.WorkerWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/master")
public class MasterController {

    @Autowired
    private LeaderElector leaderElector;

    @Autowired
    private WorkerWatcher workerWatcher;

    @GetMapping("/status")
    public String status() {
        return leaderElector.isLeader() ? "Active" : "Standby";
    }

    @PostMapping("/switch")
    public String switchLeader() {
        leaderElector.surrenderLeadership();
        return "主备切换触发成功，请稍后查看状态";
    }

    @GetMapping("/workers")
    public Collection<WorkerInfo> listWorkers() {
        return workerWatcher.getWorkerCache().values();
    }
}