package com.dex.worker.executor;

import com.dex.common.model.task.TaskConfig;
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
public class BatchTaskExecutor {

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

        log.info("开始执行批处理任务: taskId={}, instanceId={}, mode={}", task.getTaskId(), instanceId, task.getMode());

        // ✅ 传递 taskId 给 PluginManager
        Source<?> source = pluginManager.createSource(task.getSource(), task.getTaskId());
        Sink<?> sink = pluginManager.createSink(task.getSink());

        long total = 0;
        int batchSize = task.getBatchSize() != null ? task.getBatchSize() : 1000;
        String errorMsg = null;

        try {
            //source.open(task.getSource().getParams());
            //sink.open(task.getSink().getParams());

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                List<?> rawData = source.read(batchSize);
                if (rawData == null || rawData.isEmpty()) {
                    break;
                }

                @SuppressWarnings("unchecked")
                Sink<Object> objectSink = (Sink<Object>) sink;
                objectSink.write((List<Object>) rawData);

                // Sink 成功后立即提交水位线
                source.commitWatermark();

                total += rawData.size();
                log.debug("批处理进度: taskId={}, 已处理={} 条", task.getTaskId(), total);

                if (!"STREAMING".equals(task.getMode()) && rawData.size() < batchSize) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("批处理任务执行失败", e);
            errorMsg = e.getMessage();
            throw e;
        } finally {
            source.close();
            sink.close();
            runningFlags.remove(key);
            log.info("批处理任务结束: taskId={}, instanceId={}, total={}", task.getTaskId(), instanceId, total);
            notifyMaster(instanceId, errorMsg == null ? "SUCCESS" : "FAILED", total, errorMsg);
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
        log.info("批处理任务收到停止信号: {}", key);
    }
}