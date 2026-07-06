package com.dex.web.discovery;

import com.dex.common.registry.ServiceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MasterDiscovery {

    @Autowired(required = false)
    private ServiceRegistry registry;

    @Value("${dex.master.fallback-address:http://localhost:8080}")
    private String fallbackAddress;

    private String cachedAddress;
    private long cacheTime;
    private static final long CACHE_TTL = 30000; // 30秒

    public String getActiveMasterAddress() {
        long now = System.currentTimeMillis();
        if (cachedAddress != null && (now - cacheTime) < CACHE_TTL) {
            return cachedAddress;
        }

        String address = null;

        // 尝试从 ServiceRegistry 获取（ZK 模式）
        if (registry != null) {
            try {
                String leaderAddr = registry.getLeaderAddress();
                if (leaderAddr != null && !leaderAddr.isEmpty()) {
                    address = "http://" + leaderAddr;
                    log.debug("从 ServiceRegistry 获取 Master 地址: {}", address);
                }
            } catch (Exception e) {
                log.warn("从 ServiceRegistry 获取 Master 地址失败", e);
            }
        }

        // 若获取失败，使用 fallback（Local 模式）
        if (address == null) {
            address = fallbackAddress;
            log.debug("使用 fallback Master 地址: {}", address);
        }

        cachedAddress = address;
        cacheTime = now;
        return address;
    }
}