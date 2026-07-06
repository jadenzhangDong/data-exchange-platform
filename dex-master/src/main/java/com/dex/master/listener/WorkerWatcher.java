package com.dex.master.listener;

import com.dex.common.model.WorkerInfo;
import com.dex.common.registry.ServiceRegistry;
import com.dex.common.repository.InMemoryTaskRepository;
import com.dex.master.dispatch.TaskDispatcher;
import com.dex.master.lifecycle.MasterLifecycleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WorkerWatcher {

    @Autowired
    private ServiceRegistry registry;

    @Autowired
    private InMemoryTaskRepository repository;

    @Autowired
    @Lazy  // 打破循环依赖
    private MasterLifecycleManager lifecycleManager;

    @Autowired
    @Lazy  // 打破循环依赖
    private TaskDispatcher taskDispatcher;

    private final ConcurrentHashMap<String, WorkerInfo> workerCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        registry.watchWorkers(workers -> {
            workerCache.clear();
            for (WorkerInfo w : workers) {
                workerCache.put(w.getWorkerId(), w);
            }
            log.info("Worker 缓存更新，当前在线: {}", workerCache.size());

            if (lifecycleManager != null && lifecycleManager.isActive()) {
                // 仅 Active Master 处理 Worker 下线逻辑
                // 具体重分配逻辑由 ServiceRegistry 触发
            }
        });
    }

    public ConcurrentHashMap<String, WorkerInfo> getWorkerCache() {
        return workerCache;
    }
}