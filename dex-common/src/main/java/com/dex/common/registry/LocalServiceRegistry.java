package com.dex.common.registry;

import com.dex.common.model.WorkerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "dex.registry.type", havingValue = "local", matchIfMissing = true)
public class LocalServiceRegistry implements ServiceRegistry {
    private static final Logger log = LoggerFactory.getLogger(LocalServiceRegistry.class);
    private final int serverPort;
    private volatile boolean isLeader = false;
    private String leaderAddress;
    private final List<Consumer<String>> leaderListeners = new ArrayList<>();
    private final ConcurrentHashMap<String, WorkerInfo> workerMap = new ConcurrentHashMap<>();
    private Consumer<List<WorkerInfo>> workerChangeListener;
    // 超时阈值调整为 120 秒（2 分钟）
    private static final long HEARTBEAT_TIMEOUT_MS = 120000;

    public LocalServiceRegistry(@Value("${server.port}") int serverPort) {
        this.serverPort = serverPort;
    }

    @PostConstruct
    public void init() {
        log.info("Local ServiceRegistry 初始化完成");
    }

    @Override
    public synchronized void electLeader() throws Exception {
        this.isLeader = true;
        String host = InetAddress.getLocalHost().getHostAddress();
        this.leaderAddress = host + ":" + serverPort;
        log.info("Local: 成为 Leader, 地址={}", leaderAddress);
        leaderListeners.forEach(l -> l.accept(leaderAddress));
    }

    @Override
    public synchronized void surrenderLeader() {
        if (isLeader) {
            isLeader = false;
            leaderAddress = null;
            leaderListeners.forEach(l -> l.accept(null));
            log.warn("Local: 释放 Leader");
        }
    }

    @Override
    public boolean isLeader() {
        return isLeader;
    }

    @Override
    public String getLeaderAddress() {
        return leaderAddress;
    }

    @Override
    public void addLeaderListener(Consumer<String> listener) {
        leaderListeners.add(listener);
        if (leaderAddress != null) listener.accept(leaderAddress);
    }

    @Override
    public void watchWorkers(Consumer<List<WorkerInfo>> listener) {
        this.workerChangeListener = listener;
        listener.accept(new ArrayList<>(workerMap.values()));
    }

    @Override
    public void registerWorker(WorkerInfo info) throws Exception {
        workerMap.put(info.getWorkerId(), info);
        log.info("Local: Worker 注册: {}", info.getWorkerId());
        if (workerChangeListener != null) {
            workerChangeListener.accept(new ArrayList<>(workerMap.values()));
        }
    }

    @Override
    public void heartbeat(WorkerInfo info) throws Exception {
        WorkerInfo existing = workerMap.get(info.getWorkerId());
        if (existing != null) {
            existing.setLoad(info.getLoad());
            existing.setLastHeartbeat(System.currentTimeMillis());
            // 如果之前被标记为可疑或下线，但心跳恢复，则自动恢复状态
            if (!"ONLINE".equals(existing.getStatus())) {
                existing.setStatus("ONLINE");
                log.info("Local: Worker {} 心跳恢复，状态自动恢复为 ONLINE", info.getWorkerId());
                if (workerChangeListener != null) {
                    workerChangeListener.accept(new ArrayList<>(workerMap.values()));
                }
            }
        } else {
            // 如果未注册，重新注册
            registerWorker(info);
        }
    }

    @Override
    public void unregisterWorker(String workerId) throws Exception {
        workerMap.remove(workerId);
        log.info("Local: Worker 注销: {}", workerId);
        if (workerChangeListener != null) {
            workerChangeListener.accept(new ArrayList<>(workerMap.values()));
        }
    }

    @Override
    public List<WorkerInfo> getOnlineWorkers() {
        return new ArrayList<>(workerMap.values());
    }

    @Override
    public void close() {
        // nothing
    }

    @Scheduled(fixedRate = 15000)
    public void evictDeadWorkers() {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();
        for (WorkerInfo info : workerMap.values()) {
            long diff = now - info.getLastHeartbeat();
            if (diff > HEARTBEAT_TIMEOUT_MS) {
                log.warn("Local: Worker {} 心跳超时，最后心跳时间: {}, 超时: {} ms",
                        info.getWorkerId(), new java.util.Date(info.getLastHeartbeat()), diff);
                toRemove.add(info.getWorkerId());
            }
        }
        if (!toRemove.isEmpty()) {
            for (String workerId : toRemove) {
                workerMap.remove(workerId);
                log.warn("Local: Worker 已剔除: {}", workerId);
            }
            if (workerChangeListener != null) {
                workerChangeListener.accept(new ArrayList<>(workerMap.values()));
            }
        }
    }

    @PreDestroy
    public void destroy() {
        close();
    }
}