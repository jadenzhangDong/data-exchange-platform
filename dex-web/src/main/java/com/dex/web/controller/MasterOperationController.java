package com.dex.web.controller;

import com.dex.web.discovery.MasterDiscovery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/web/operation")
public class MasterOperationController {

    @Autowired
    private MasterDiscovery masterDiscovery;

    private final WebClient webClient = WebClient.create();

    @PostMapping("/switch-master")
    public Mono<String> switchMaster() {
        String masterAddr = masterDiscovery.getActiveMasterAddress();
        return webClient.post()
                .uri(masterAddr + "/api/master/switch")
                .retrieve()
                .bodyToMono(String.class);
    }
}