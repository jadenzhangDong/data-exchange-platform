package com.dex.master.controller;

import com.dex.common.model.WorkerInfo;
import com.dex.common.model.entity.TaskInstanceEntity;
import com.dex.common.registry.ServiceRegistry;
import com.dex.common.repository.TaskInstanceRepository;
import com.dex.master.listener.WorkerWatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/master/worker")
public class MasterWorkerController {

    @Autowired
    private ServiceRegistry registry;

    @Autowired
    private WorkerWatcher workerWatcher;

    @Autowired
    private TaskInstanceRepository taskInstanceRepo;

    // ===== 原有接口 =====

    @PostMapping("/register")
    public String register(@RequestBody WorkerInfo info) throws Exception {
        log.info("收到 Worker 注册请求: {}, weight={}, tags={}",
                info.getWorkerId(), info.getWeight(), info.getTags());
        info.setStatus("ONLINE");
        info.setLastHeartbeat(System.currentTimeMillis());
        registry.registerWorker(info);
        return "registered";
    }

    @PostMapping("/heartbeat")
    public String heartbeat(@RequestBody WorkerInfo info) throws Exception {
        log.debug("收到 Worker 心跳: {}, load={}", info.getWorkerId(), info.getLoad());
        info.setLastHeartbeat(System.currentTimeMillis());
        registry.heartbeat(info);
        return "ok";
    }

    @DeleteMapping("/unregister/{workerId}")
    public String unregister(@PathVariable String workerId) throws Exception {
        log.info("收到 Worker 注销请求: {}", workerId);
        registry.unregisterWorker(workerId);
        return "unregistered";
    }

    // ===== 新增：节点管理接口 =====

    /**
     * 禁用 Worker（不再接收新任务，保留现有任务）
     */
    @PostMapping("/disable/{workerId}")
    public String disableWorker(@PathVariable String workerId) {
        WorkerInfo worker = workerWatcher.getWorkerCache().get(workerId);
        if (worker == null) {
            return "Worker 不存在";
        }
        if ("DISABLED".equals(worker.getStatus())) {
            return "Worker 已是禁用状态";
        }
        worker.setStatus("DISABLED");
        // 可选：通知 Worker 停止接收新任务（但 Worker 自己不会主动拉取，所以只需 Master 端过滤即可）
        log.info("Worker 已禁用: {}", workerId);
        return "Worker 已禁用";
    }

    /**
     * 获取 Worker 的排空状态
     */
    @GetMapping("/drain/status/{workerId}")
    public String getDrainStatus(@PathVariable String workerId) {
        WorkerInfo worker = workerWatcher.getWorkerCache().get(workerId);
        if (worker == null) {
            return "Worker 不存在";
        }
        if ("DRAINING".equals(worker.getStatus())) {
            List<TaskInstanceEntity> runningTasks =
                    taskInstanceRepo.findByAssignedWorkerIdAndState(workerId, "RUNNING");
            return "排空中，剩余 " + runningTasks.size() + " 个任务";
        }
        return "当前状态: " + worker.getStatus();
    }

    /**
     * 更新 Worker 权重（影响任务分发概率）
     */
    @PostMapping("/weight/{workerId}")
    public String updateWeight(@PathVariable String workerId, @RequestParam int weight) {
        if (weight < 1 || weight > 100) {
            return "权重必须在 1-100 之间";
        }
        WorkerInfo worker = workerWatcher.getWorkerCache().get(workerId);
        if (worker == null) {
            return "Worker 不存在";
        }
        worker.setWeight(weight);
        log.info("Worker 权重已更新: {} -> {}", workerId, weight);
        return "Worker 权重已更新为 " + weight;
    }

    /**
     * 获取 Worker 详细指标（预留，可扩展）
     */
    @GetMapping("/metrics/{workerId}")
    public WorkerInfo getWorkerMetrics(@PathVariable String workerId) {
        return workerWatcher.getWorkerCache().get(workerId);
    }

    @PostMapping("/drain/{workerId}")
    public String drainWorker(@PathVariable String workerId) {
        WorkerInfo worker = workerWatcher.getWorkerCache().get(workerId);
        if (worker == null) {
            return "Worker 不存在";
        }
        if ("DRAINING".equals(worker.getStatus())) {
            return "Worker 已在排空模式";
        }
        // 无论是否有任务，统一设置为 DRAINING（不再自动下线）
        worker.setStatus("DRAINING");
        log.info("Worker 进入排空模式: {}, 不再接收新任务", workerId);
        return "Worker 已进入排空模式（不再接收新任务）";
    }

    @PostMapping("/enable/{workerId}")
    public String enableWorker(@PathVariable String workerId) {
        WorkerInfo worker = workerWatcher.getWorkerCache().get(workerId);
        if (worker == null) {
            return "Worker 不存在";
        }
        if ("ONLINE".equals(worker.getStatus())) {
            return "Worker 已是在线状态";
        }
        worker.setStatus("ONLINE");
        log.info("Worker 已启用并恢复在线: {}", workerId);
        return "Worker 已启用并恢复在线";
    }
}