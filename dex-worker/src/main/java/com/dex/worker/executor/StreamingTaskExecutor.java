package com.dex.worker.executor;

import com.dex.common.model.task.TaskConfig;
import com.dex.common.model.task.StreamingConfig;
import com.dex.plugin.api.Sink;
import com.dex.plugin.api.Source;
import com.dex.worker.plugin.PluginManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class StreamingTaskExecutor {

    @Autowired
    private PluginManager pluginManager;

    private final Map<String, AtomicBoolean> runningFlags = new java.util.concurrent.ConcurrentHashMap<>();

    public void execute(TaskConfig task, String instanceId) throws Exception {
        String key = task.getTaskId() + "-" + instanceId;
        AtomicBoolean running = new AtomicBoolean(true);
        runningFlags.put(key, running);

        log.info("开始执行流式任务: taskId={}, instanceId={}", task.getTaskId(), instanceId);

        // ✅ 传递 taskId 给 PluginManager
        Source<?> source = pluginManager.createSource(task.getSource(), task.getTaskId());
        Sink<?> sink = pluginManager.createSink(task.getSink());

        StreamingConfig streamCfg = task.getStreamingConfig();
        long maxWaitMs = streamCfg != null ? streamCfg.getMaxWaitMs() : 5000L;

        long total = 0;

        try {
            //source.open(task.getSource().getParams());
            //sink.open(task.getSink().getParams());

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                List<?> rawData = source.read(task.getBatchSize());

                if (rawData != null && !rawData.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Sink<Object> objectSink = (Sink<Object>) sink;
                    objectSink.write((List<Object>) rawData);

                    // Sink 成功后立即提交水位线
                    source.commitWatermark();

                    total += rawData.size();
                    log.debug("流式任务处理: taskId={}, total={}", task.getTaskId(), total);
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