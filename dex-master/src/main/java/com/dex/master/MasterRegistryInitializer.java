package com.dex.master;

import com.dex.common.registry.ServiceRegistry;
import com.dex.master.lifecycle.MasterLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MasterRegistryInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(MasterRegistryInitializer.class);

    @Autowired
    private ServiceRegistry registry;

    @Autowired
    private MasterLifecycleManager lifecycleManager;

    @Override
    public void run(String... args) throws Exception {
        // 注册 Leader 变更监听器
        registry.addLeaderListener(address -> {
            if (address != null) {
                log.info("收到 Leader 变更通知：成为 Leader (address={})", address);
                lifecycleManager.onBecomeActive();
            } else {
                log.info("收到 Leader 变更通知：失去 Leader");
                lifecycleManager.onBecomeStandby();
            }
        });

        // 开始选举
        registry.electLeader();

        // 如果当前已经是 Leader（local 模式下，electLeader 是同步的），主动触发
        if (registry.isLeader()) {
            log.info("当前已是 Leader，主动触发 onBecomeActive");
            lifecycleManager.onBecomeActive();
        }

        // 注意：Worker 监听由 WorkerWatcher 负责，这里不再重复添加
        log.info("Master 初始化完成，isLeader={}", registry.isLeader());
    }
}