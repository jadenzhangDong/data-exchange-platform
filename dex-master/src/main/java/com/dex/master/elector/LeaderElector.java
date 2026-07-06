package com.dex.master.elector;

import com.dex.common.registry.ServiceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LeaderElector {

    @Autowired
    private ServiceRegistry registry;

    public boolean isLeader() {
        return registry.isLeader();
    }

    public void surrenderLeadership() {
        registry.surrenderLeader();
        log.info("主动释放领导权");
    }

    public String getLeaderInfo() {
        return registry.getLeaderAddress();
    }
}