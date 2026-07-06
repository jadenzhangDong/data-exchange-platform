package com.dex.worker.registry;

import com.dex.common.model.WorkerInfo;
import com.dex.common.registry.ServiceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WorkerRegistry {

    @Autowired(required = false)
    private ServiceRegistry registry;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    @Value("${dex.registry.type:local}")
    private String registryType;

    @Value("${dex.master.address:http://localhost:8080}")
    private String masterAddress;

    @Value("${worker.port:8081}")
    private int port;

    @Value("${worker.grpc.port:19090}")
    private int grpcPort;

    // 权重和标签配置
    @Value("${worker.weight:10}")
    private int weight;

    @Value("${worker.tags:}")
    private String tagsStr;

    private String workerId;
    private WorkerInfo workerInfo;

    @PostConstruct
    public void init() throws Exception {
        String ip = InetAddress.getLocalHost().getHostAddress();
        String pid = String.valueOf(getCurrentPid());
        workerId = "worker-" + ip + "-" + pid;

        // 解析标签
        List<String> tags = Arrays.stream(tagsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        workerInfo = new WorkerInfo();
        workerInfo.setWorkerId(workerId);
        workerInfo.setHost(ip);
        workerInfo.setPort(port);
        workerInfo.setGrpcPort(grpcPort);
        workerInfo.setStatus("ONLINE");
        workerInfo.setLoad(0);
        workerInfo.setWeight(weight);
        workerInfo.setTags(tags);
        workerInfo.setLastHeartbeat(System.currentTimeMillis());

        if ("zk".equals(registryType)) {
            if (registry != null) {
                registry.registerWorker(workerInfo);
                log.info("Worker 通过 ZK 注册成功: {}, weight={}, tags={}", workerId, weight, tags);
            } else {
                log.error("ZK 模式但 ServiceRegistry 未注入");
            }
        } else {
            registerViaHttp();
        }
    }

    private void registerViaHttp() {
        try {
            if (restTemplate == null) {
                log.warn("RestTemplate 未注入，使用模拟注册");
                return;
            }
            String url = masterAddress + "/api/master/worker/register";
            restTemplate.postForObject(url, workerInfo, String.class);
            log.info("Worker 通过 HTTP 注册成功: {}, weight={}, tags={}", workerId, weight, workerInfo.getTags());
        } catch (Exception e) {
            log.error("HTTP 注册失败", e);
        }
    }

    @Scheduled(fixedRate = 10000)
    public void heartbeat() throws Exception {
        workerInfo.setLoad((int) (Math.random() * 10));
        workerInfo.setLastHeartbeat(System.currentTimeMillis());

        if ("zk".equals(registryType)) {
            if (registry != null) {
                registry.heartbeat(workerInfo);
                log.debug("ZK 心跳发送成功, load={}", workerInfo.getLoad());
            }
        } else {
            heartbeatViaHttp();
        }
    }

    private void heartbeatViaHttp() {
        try {
            if (restTemplate == null) return;
            String url = masterAddress + "/api/master/worker/heartbeat";
            restTemplate.postForObject(url, workerInfo, String.class);
            log.debug("HTTP 心跳发送成功, load={}", workerInfo.getLoad());
        } catch (Exception e) {
            log.warn("HTTP 心跳失败，尝试重新注册", e);
            registerViaHttp();
        }
    }

    @PreDestroy
    public void destroy() throws Exception {
        if ("zk".equals(registryType)) {
            if (registry != null) {
                registry.unregisterWorker(workerId);
            }
        } else {
            try {
                if (restTemplate != null) {
                    String url = masterAddress + "/api/master/worker/unregister/" + workerId;
                    restTemplate.delete(url);
                }
            } catch (Exception e) {
                log.warn("注销失败", e);
            }
        }
        log.info("Worker 注销成功");
    }

    private int getCurrentPid() {
        try {
            String name = ManagementFactory.getRuntimeMXBean().getName();
            int atIndex = name.indexOf('@');
            if (atIndex > 0) {
                return Integer.parseInt(name.substring(0, atIndex));
            }
        } catch (Exception e) {
            // ignore
        }
        return (int) (Math.random() * 10000);
    }

    public String getWorkerId() {
        return workerId;
    }

    public WorkerInfo getWorkerInfo() {
        return workerInfo;
    }
}