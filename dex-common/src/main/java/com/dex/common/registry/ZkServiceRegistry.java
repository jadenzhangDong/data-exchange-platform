package com.dex.common.registry;

import com.dex.common.constants.ZkPaths;
import com.dex.common.model.WorkerInfo;
import com.dex.common.util.JsonUtil;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "dex.registry.type", havingValue = "zk")
public class ZkServiceRegistry implements ServiceRegistry {
    private static final Logger log = LoggerFactory.getLogger(ZkServiceRegistry.class);

    private final CuratorFramework zkClient;
    private final int serverPort;

    private LeaderSelector leaderSelector;
    private CountDownLatch leadershipLatch = new CountDownLatch(1);
    private volatile boolean isLeader = false;
    private String leaderAddress;
    private final List<Consumer<String>> leaderListeners = new ArrayList<>();

    private PathChildrenCache workerCache;
    private final ConcurrentHashMap<String, WorkerInfo> workerMap = new ConcurrentHashMap<>();
    private Consumer<List<WorkerInfo>> workerChangeListener;

    // 延迟确认调度器
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ZkServiceRegistry(CuratorFramework zkClient, @Value("${server.port}") int serverPort) {
        this.zkClient = zkClient;
        this.serverPort = serverPort;
    }

    @PostConstruct
    public void init() throws Exception {
        if (zkClient.checkExists().forPath(ZkPaths.MASTER_ELECTION) == null) {
            zkClient.create().creatingParentsIfNeeded().forPath(ZkPaths.MASTER_ELECTION);
        }
        if (zkClient.checkExists().forPath(ZkPaths.WORKERS_REGISTRY) == null) {
            zkClient.create().creatingParentsIfNeeded().forPath(ZkPaths.WORKERS_REGISTRY);
        }

        workerCache = new PathChildrenCache(zkClient, ZkPaths.WORKERS_REGISTRY, true);
        workerCache.getListenable().addListener((client, event) -> {
            if (event.getData() == null) return;
            String path = event.getData().getPath();
            String workerId = path.substring(path.lastIndexOf('/') + 1);
            WorkerInfo info = JsonUtil.fromJson(new String(event.getData().getData()), WorkerInfo.class);
            if (info == null) return;

            switch (event.getType()) {
                case CHILD_ADDED:
                case CHILD_UPDATED:
                    // 当节点添加或更新时，更新缓存，并取消可能的下线标记（若有）
                    workerMap.put(workerId, info);
                    log.debug("ZK: Worker {} 状态更新", workerId);
                    if (workerChangeListener != null) {
                        workerChangeListener.accept(new ArrayList<>(workerMap.values()));
                    }
                    break;
                case CHILD_REMOVED:
                    // 延迟 10 秒后确认节点是否真的消失（去抖动）
                    scheduler.schedule(() -> {
                        try {
                            // 再次检查节点是否存在
                            if (zkClient.checkExists().forPath(path) == null) {
                                // 节点确实不存在，确认下线
                                WorkerInfo removed = workerMap.remove(workerId);
                                if (removed != null) {
                                    log.warn("ZK: Worker 下线确认: {}, 最后心跳时间: {}",
                                            workerId, new Date(removed.getLastHeartbeat()));
                                    if (workerChangeListener != null) {
                                        workerChangeListener.accept(new ArrayList<>(workerMap.values()));
                                    }
                                }
                            } else {
                                log.info("ZK: Worker {} 重新出现，忽略下线事件", workerId);
                                // 重新获取节点数据，更新缓存
                                try {
                                    byte[] data = zkClient.getData().forPath(path);
                                    if (data != null) {
                                        WorkerInfo freshInfo = JsonUtil.fromJson(new String(data), WorkerInfo.class);
                                        if (freshInfo != null) {
                                            workerMap.put(workerId, freshInfo);
                                            if (workerChangeListener != null) {
                                                workerChangeListener.accept(new ArrayList<>(workerMap.values()));
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    log.error("获取 Worker 数据失败: {}", workerId, e);
                                }
                            }
                        } catch (Exception e) {
                            log.error("检查 Worker 节点失败: {}", workerId, e);
                        }
                    }, 10, TimeUnit.SECONDS);
                    break;
                default:
                    break;
            }
        });
        workerCache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
        log.info("ZK ServiceRegistry 初始化完成");
    }

    @Override
    public void electLeader() throws Exception {
        leaderSelector = new LeaderSelector(zkClient, ZkPaths.MASTER_ELECTION,
                new LeaderSelectorListenerAdapter() {
                    @Override
                    public void takeLeadership(CuratorFramework client) throws Exception {
                        isLeader = true;
                        String host = InetAddress.getLocalHost().getHostAddress();
                        leaderAddress = host + ":" + serverPort;
                        String leaderPath = ZkPaths.LEADER_INFO;
                        if (zkClient.checkExists().forPath(leaderPath) != null) {
                            zkClient.setData().forPath(leaderPath, leaderAddress.getBytes());
                        } else {
                            zkClient.create().creatingParentsIfNeeded().forPath(leaderPath, leaderAddress.getBytes());
                        }
                        log.info("ZK: 成为 Leader, 地址={}", leaderAddress);
                        leaderListeners.forEach(l -> l.accept(leaderAddress));
                        leadershipLatch.await();

                        // 失去 Leader
                        isLeader = false;
                        try {
                            if (zkClient.checkExists().forPath(ZkPaths.LEADER_INFO) != null) {
                                zkClient.delete().forPath(ZkPaths.LEADER_INFO);
                            }
                        } catch (Exception e) {
                            log.warn("删除 Leader 信息失败", e);
                        }
                        leaderAddress = null;
                        leaderListeners.forEach(l -> l.accept(null));
                        log.warn("ZK: 失去 Leader");
                    }
                });
        leaderSelector.autoRequeue();
        leaderSelector.start();
        log.info("ZK LeaderSelector 启动");
    }

    @Override
    public void surrenderLeader() {
        if (leaderSelector != null && isLeader) {
            leadershipLatch.countDown();
        }
    }

    @Override
    public boolean isLeader() {
        return isLeader;
    }

    @Override
    public String getLeaderAddress() {
        try {
            if (zkClient.checkExists().forPath(ZkPaths.LEADER_INFO) != null) {
                byte[] data = zkClient.getData().forPath(ZkPaths.LEADER_INFO);
                if (data != null) return new String(data);
            }
        } catch (Exception e) {
            log.error("获取 Leader 地址失败", e);
        }
        return null;
    }

    @Override
    public void addLeaderListener(Consumer<String> listener) {
        leaderListeners.add(listener);
        String addr = getLeaderAddress();
        if (addr != null) listener.accept(addr);
    }

    @Override
    public void watchWorkers(Consumer<List<WorkerInfo>> listener) {
        this.workerChangeListener = listener;
        listener.accept(new ArrayList<>(workerMap.values()));
    }

    @Override
    public void registerWorker(WorkerInfo info) throws Exception {
        String path = ZkPaths.WORKERS_REGISTRY + "/" + info.getWorkerId();
        if (zkClient.checkExists().forPath(path) != null) {
            zkClient.delete().forPath(path);
        }
        zkClient.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL)
                .forPath(path, JsonUtil.toJson(info).getBytes());
        log.info("ZK: Worker 注册成功: {}", info.getWorkerId());
    }

    @Override
    public void heartbeat(WorkerInfo info) throws Exception {
        String path = ZkPaths.WORKERS_REGISTRY + "/" + info.getWorkerId();
        if (zkClient.checkExists().forPath(path) != null) {
            zkClient.setData().forPath(path, JsonUtil.toJson(info).getBytes());
        } else {
            registerWorker(info);
        }
    }

    @Override
    public void unregisterWorker(String workerId) throws Exception {
        String path = ZkPaths.WORKERS_REGISTRY + "/" + workerId;
        if (zkClient.checkExists().forPath(path) != null) {
            zkClient.delete().quietly().forPath(path);
        }
    }

    @Override
    public List<WorkerInfo> getOnlineWorkers() {
        return new ArrayList<>(workerMap.values());
    }

    @Override
    public void close() {
        if (leaderSelector != null) leaderSelector.close();
        try {
            if (workerCache != null) workerCache.close();
        } catch (Exception e) {
            // ignore
        }
    }

    @PreDestroy
    public void destroy() {
        close();
    }
}