package com.dex.common.registry;

import com.dex.common.model.WorkerInfo;
import java.util.List;
import java.util.function.Consumer;

public interface ServiceRegistry {
    // Master 侧
    void electLeader() throws Exception;
    void surrenderLeader();
    boolean isLeader();
    String getLeaderAddress();
    void addLeaderListener(Consumer<String> listener);
    void watchWorkers(Consumer<List<WorkerInfo>> listener);

    // Worker 侧
    void registerWorker(WorkerInfo info) throws Exception;
    void heartbeat(WorkerInfo info) throws Exception;
    void unregisterWorker(String workerId) throws Exception;
    List<WorkerInfo> getOnlineWorkers();

    void close();
}