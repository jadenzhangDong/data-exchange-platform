package com.dex.master.dispatch;

import com.dex.common.model.WorkerInfo;
import com.dex.common.model.task.TaskConfig;
import com.dex.master.listener.WorkerWatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(name = "dex.communication.protocol", havingValue = "rest", matchIfMissing = true)
public class RestTaskDispatcher implements TaskDispatcher {

    @Autowired
    @Lazy
    private WorkerWatcher workerWatcher;

    private final WebClient webClient = WebClient.create();

    @Override
    public void dispatchTask(TaskConfig task, String instanceId) {
        dispatchTask(task, instanceId, null);
    }

    @Override
    public void dispatchTask(TaskConfig task, String instanceId, String requiredTag) {
        List<WorkerInfo> candidates = workerWatcher.getWorkerCache().values().stream()
                .filter(w -> isWorkerAvailable(w))
                .filter(w -> matchTag(w, requiredTag))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            log.error("没有可用的 Worker 节点（标签要求: {}），任务 {} 分发失败", requiredTag, task.getTaskId());
            return;
        }

        WorkerInfo target = weightedRandom(candidates);
        if (target == null) {
            log.error("无法选择 Worker");
            return;
        }

        String url = String.format("http://%s:%d/api/worker/execute", target.getHost(), target.getPort());
        log.info("REST 分发任务 {} (实例 {}) 到 Worker {} (权重={}, load={})",
                task.getTaskId(), instanceId, target.getWorkerId(), target.getWeight(), target.getLoad());

        webClient.post()
                .uri(url)
                .bodyValue(task)
                .header("X-Instance-Id", instanceId)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                        resp -> log.info("Worker {} 接收任务成功: {}", target.getWorkerId(), resp),
                        err -> log.error("Worker {} 接收任务失败", target.getWorkerId(), err)
                );
    }

    @Override
    public void stopTask(String taskId, String instanceId) {
        workerWatcher.getWorkerCache().values().forEach(worker -> {
            String url = String.format("http://%s:%d/api/worker/stop", worker.getHost(), worker.getPort());
            // ===== 兼容 Java 8：使用 HashMap 代替 Map.of =====
            Map<String, String> req = new HashMap<>();
            req.put("taskId", taskId);
            req.put("instanceId", instanceId);
            webClient.post()
                    .uri(url)
                    .bodyValue(req)
                    .retrieve()
                    .toBodilessEntity()
                    .subscribe(
                            resp -> log.debug("REST 停止任务 {} 成功", taskId),
                            err -> log.warn("REST 停止任务 {} 失败: {}", taskId, err.getMessage())
                    );
        });
    }

    private boolean isWorkerAvailable(WorkerInfo worker) {
        return "ONLINE".equals(worker.getStatus());
    }

    private boolean matchTag(WorkerInfo worker, String requiredTag) {
        if (requiredTag == null || requiredTag.isEmpty()) {
            return true;
        }
        if (worker.getTags() == null || worker.getTags().isEmpty()) {
            return false;
        }
        return worker.getTags().stream().anyMatch(tag -> tag.equals(requiredTag));
    }

    private WorkerInfo weightedRandom(List<WorkerInfo> workers) {
        int totalWeight = workers.stream().mapToInt(WorkerInfo::getWeight).sum();
        if (totalWeight <= 0) {
            return workers.get(ThreadLocalRandom.current().nextInt(workers.size()));
        }
        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        for (WorkerInfo w : workers) {
            random -= w.getWeight();
            if (random < 0) {
                return w;
            }
        }
        return workers.get(workers.size() - 1);
    }
}