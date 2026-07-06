package com.dex.common.repository;

import com.dex.common.model.entity.ReconciliationConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationConfigRepository extends JpaRepository<ReconciliationConfigEntity, String> {
    List<ReconciliationConfigEntity> findByEnabledTrue();
}
