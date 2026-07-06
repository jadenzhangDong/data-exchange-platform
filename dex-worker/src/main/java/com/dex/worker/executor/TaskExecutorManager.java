package com.dex.worker.executor;

import com.dex.common.model.task.TaskConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Slf4j
@Component
public class TaskExecutorManager {

    @Autowired
    private BatchTaskExecutor batchExecutor;

    @Autowired
    private StreamingTaskExecutor streamingExecutor;

    private ThreadPoolTaskExecutor threadPool;
    private final Map<String, Future<?>> runningFutures = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        threadPool = new ThreadPoolTaskExecutor();
        threadPool.setCorePoolSize(10);
        threadPool.setMaxPoolSize(50);
        threadPool.setQueueCapacity(100);
        threadPool.setThreadNamePrefix("worker-exec-");
        threadPool.initialize();
        log.info("Worker 线程池初始化完成");
    }

    public void submit(TaskConfig task, String instanceId) {
        String key = task.getTaskId() + "-" + instanceId;
        Future<?> future = null;

        switch (task.getMode()) {
            case "ONESHOT":
            case "BATCH":
            case "SCHEDULED":
                future = threadPool.submit(() -> {
                    try {
                        batchExecutor.execute(task, instanceId);
                    } catch (Exception e) {
                        log.error("批处理任务执行失败: {}", key, e);
                    }
                });
                break;
            case "STREAMING":
                future = threadPool.submit(() -> {
                    try {
                        streamingExecutor.execute(task, instanceId);
                    } catch (Exception e) {
                        log.error("流式任务执行失败: {}", key, e);
                    }
                });
                break;
            default:
                log.error("未知任务模式: {}", task.getMode());
        }

        if (future != null) {
            runningFutures.put(key, future);
            log.info("任务 {} 已提交执行", key);
        }
    }

    public void stop(String taskId, String instanceId) {
        String key = taskId + "-" + instanceId;
        Future<?> future = runningFutures.remove(key);
        if (future != null) {
            boolean canceled = future.cancel(true);
            log.info("任务 {} 停止请求, 取消状态: {}", key, canceled);
        } else {
            log.warn("未找到正在运行的任务: {}", key);
        }
        // 双重保障：设置执行器的停止标志
        batchExecutor.stop(taskId, instanceId);
        streamingExecutor.stop(taskId, instanceId);
    }
}