package com.dex.common.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "com.dex.common.model.entity")
@EnableJpaRepositories(basePackages = "com.dex.common.repository")
public class JpaConfig {
}