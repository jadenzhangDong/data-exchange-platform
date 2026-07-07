package com.dex.worker.executor;

import com.dex.common.model.task.TaskConfig;
import com.dex.common.model.task.StreamingConfig;
import com.dex.plugin.api.Sink;
import com.dex.plugin.api.Source;
import com.dex.worker.plugin.PluginManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class StreamingTaskExecutor {

    @Autowired
    private PluginManager pluginManager;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${dex.master.address:http://localhost:8080}")
    private String masterAddress;

    private final Map<String, AtomicBoolean> runningFlags = new java.util.concurrent.ConcurrentHashMap<>();

    public void execute(TaskConfig task, String instanceId) throws Exception {
        String key = task.getTaskId() + "-" + instanceId;
        AtomicBoolean running = new AtomicBoolean(true);
        runningFlags.put(key, running);

        log.info("开始执行流式任务: taskId={}, instanceId={}", task.getTaskId(), instanceId);

        Source<?> source = pluginManager.createSource(task.getSource(), task.getTaskId());
        Sink<?> sink = pluginManager.createSink(task.getSink());

        StreamingConfig streamCfg = task.getStreamingConfig();
        long maxWaitMs = streamCfg != null ? streamCfg.getMaxWaitMs() : 5000L;

        long total = 0;

        try {
            source.open(task.getSource().getParams());
            sink.open(task.getSink().getParams());

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                List<?> rawData = source.read(task.getBatchSize());

                if (rawData != null && !rawData.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Sink<Object> objectSink = (Sink<Object>) sink;
                    objectSink.write((List<Object>) rawData);

                    source.commitWatermark();

                    total += rawData.size();
                    log.debug("流式任务处理: taskId={}, total={}", task.getTaskId(), total);

                    // ✅ 每批数据后上报心跳
                    reportHeartbeat(instanceId, total);
                } else {
                    Thread.sleep(maxWaitMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("流式任务被中断: {}", task.getTaskId());
        } catch (Exception e) {
            log.error("流式任务执行失败: {}", task.getTaskId(), e);
            throw e;
        } finally {
            source.close();
            sink.close();
            runningFlags.remove(key);
            log.info("流式任务结束: taskId={}, instanceId={}, total={}", task.getTaskId(), instanceId, total);
            // 流式任务一般不会结束，但如果结束，通知 Master
            notifyMaster(instanceId, "STOPPED", total, null);
        }
    }

    /**
     * 上报任务心跳（在 Worker 端调用）
     */
    private void reportHeartbeat(String instanceId, long processed) {
        try {
            String url = masterAddress + "/api/master/task/heartbeat";
            Map<String, Object> params = new HashMap<>();
            params.put("instanceId", instanceId);
            params.put("processedRecords", processed);
            restTemplate.postForObject(url, params, String.class);
        } catch (Exception e) {
            log.warn("心跳上报失败: {}", e.getMessage());
        }
    }

    private void notifyMaster(String instanceId, String state, long processed, String error) {
        try {
            String url = masterAddress + "/api/master/worker/task/complete";
            Map<String, String> params = new HashMap<>();
            params.put("instanceId", instanceId);
            params.put("state", state);
            params.put("processed", String.valueOf(processed));
            if (error != null) {
                params.put("error", error);
            }
            restTemplate.postForObject(url, params, String.class);
        } catch (Exception e) {
            log.warn("回调 Master 失败", e);
        }
    }

    public void stop(String taskId, String instanceId) {
        String key = taskId + "-" + instanceId;
        AtomicBoolean flag = runningFlags.get(key);
        if (flag != null) {
            flag.set(false);
        }
        log.info("流式任务收到停止信号: {}", key);
    }
}