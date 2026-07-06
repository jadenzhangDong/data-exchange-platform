package com.dex.worker.controller;

import com.dex.common.model.task.TaskConfig;
import com.dex.worker.executor.TaskExecutorManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/worker")
public class WorkerTaskController {

    @Autowired
    private TaskExecutorManager executorManager;

    @PostMapping("/execute")
    public String execute(@RequestBody TaskConfig task, @RequestHeader("X-Instance-Id") String instanceId) {
        log.info("REST 收到任务执行请求: taskId={}, mode={}, instanceId={}",
                task.getTaskId(), task.getMode(), instanceId);
        executorManager.submit(task, instanceId);
        return "ACCEPTED";
    }

    @PostMapping("/stop")
    public String stop(@RequestBody Map<String, String> request) {
        String taskId = request.get("taskId");
        String instanceId = request.get("instanceId");
        executorManager.stop(taskId, instanceId);
        return "STOPPING";
    }
}