package com.dex.master.dispatch;

import com.dex.common.model.WorkerInfo;
import com.dex.common.model.task.TaskConfig;
import com.dex.common.util.JsonUtil;
import com.dex.grpc.TaskProto;
import com.dex.grpc.TaskServiceGrpc;
import com.dex.master.listener.WorkerWatcher;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(name = "dex.communication.protocol", havingValue = "grpc")
public class GrpcTaskDispatcher implements TaskDispatcher {

    @Autowired
    @Lazy
    private WorkerWatcher workerWatcher;

    private final Map<String, ManagedChannel> channelCache = new ConcurrentHashMap<>();

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

        // 加权随机选择
        WorkerInfo target = weightedRandom(candidates);
        if (target == null) {
            log.error("无法选择 Worker");
            return;
        }

        String targetHost = target.getHost() + ":" + target.getGrpcPort();
        log.info("gRPC 分发任务 {} (实例 {}) 到 Worker {} (权重={}, load={})",
                task.getTaskId(), instanceId, target.getWorkerId(), target.getWeight(), target.getLoad());

        try {
            ManagedChannel channel = channelCache.computeIfAbsent(
                    target.getWorkerId(),
                    k -> ManagedChannelBuilder.forTarget(targetHost)
                            .usePlaintext()
                            .build()
            );

            TaskServiceGrpc.TaskServiceBlockingStub stub = TaskServiceGrpc.newBlockingStub(channel);

            TaskProto.TaskRequest request = TaskProto.TaskRequest.newBuilder()
                    .setTaskId(task.getTaskId())
                    .setInstanceId(instanceId)
                    .setMode(task.getMode())
                    .setConfigJson(JsonUtil.toJson(task))
                    .build();

            TaskProto.TaskResponse response = stub.execute(request);
            log.info("gRPC 分发成功: {}", response.getMessage());

        } catch (Exception e) {
            log.error("gRPC 分发任务失败", e);
            channelCache.remove(target.getWorkerId());
        }
    }

    @Override
    public void stopTask(String taskId, String instanceId) {
        workerWatcher.getWorkerCache().values().forEach(worker -> {
            ManagedChannel channel = channelCache.get(worker.getWorkerId());
            if (channel == null) return;

            try {
                TaskServiceGrpc.TaskServiceBlockingStub stub = TaskServiceGrpc.newBlockingStub(channel);
                TaskProto.StopRequest request = TaskProto.StopRequest.newBuilder()
                        .setTaskId(taskId)
                        .setInstanceId(instanceId)
                        .build();
                stub.stop(request);
                log.debug("gRPC 停止任务 {} 成功", taskId);
            } catch (Exception e) {
                log.warn("gRPC 停止任务 {} 失败: {}", taskId, e.getMessage());
            }
        });
    }

    // ===== 辅助方法 =====

    /**
     * 判断 Worker 是否可用（ONLINE 或 DRAINING 仍可接收任务）
     */
    private boolean isWorkerAvailable(WorkerInfo worker) {
        return "ONLINE".equals(worker.getStatus());
    }

    /**
     * 匹配标签（如果 requiredTag 为空，则所有 Worker 都匹配）
     */
    private boolean matchTag(WorkerInfo worker, String requiredTag) {
        if (requiredTag == null || requiredTag.isEmpty()) {
            return true;
        }
        if (worker.getTags() == null || worker.getTags().isEmpty()) {
            return false;
        }
        return worker.getTags().stream().anyMatch(tag -> tag.equals(requiredTag));
    }

    /**
     * 加权随机选择
     */
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