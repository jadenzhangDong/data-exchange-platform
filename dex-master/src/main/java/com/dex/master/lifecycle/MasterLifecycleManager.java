package com.dex.master.lifecycle;

import com.dex.common.registry.ServiceRegistry;
import com.dex.master.scheduler.DefaultTaskScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.function.Consumer;

@Slf4j
@Component
public class MasterLifecycleManager implements Consumer<String> {

    @Autowired
    private ServiceRegistry registry;

    @Autowired
    private DefaultTaskScheduler taskScheduler;

    private volatile boolean active = false;

    @PostConstruct
    public void init() {
        registry.addLeaderListener(this);
        if (registry.isLeader()) {
            accept(registry.getLeaderAddress());
        }
    }

    @Override
    public void accept(String leaderAddress) {
        if (leaderAddress != null) {
            onBecomeActive();
        } else {
            onBecomeStandby();
        }
    }

    public synchronized void onBecomeActive() {
        if (active) return;
        active = true;
        log.info("成为 Active Master，恢复定时任务...");
        taskScheduler.rescheduleAllEnabled();
    }

    public synchronized void onBecomeStandby() {
        if (!active) return;
        active = false;
        log.warn("转为 Standby，停止本地调度");
        taskScheduler.shutdownSchedulers();
    }

    public boolean isActive() {
        return active;
    }
}