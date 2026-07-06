package com.dex.worker.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Slf4j
@Component
public class GrpcServerInitializer {

    @Value("${worker.grpc.port:19090}")
    private int grpcPort;

    @Autowired
    private TaskGrpcService taskGrpcService;

    private Server server;

    @PostConstruct
    public void start() throws Exception {
        server = ServerBuilder.forPort(grpcPort)
                .addService(taskGrpcService)
                .build()
                .start();
        log.info("gRPC Server 启动，端口: {}", grpcPort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (server != null) {
                server.shutdown();
                log.info("gRPC Server 关闭");
            }
        }));
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }
}