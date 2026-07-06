package com.dex.common.repository;

import com.dex.common.model.entity.TableMetaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TableMetaRepository extends JpaRepository<TableMetaEntity, String> {
    List<TableMetaEntity> findByDataSourceId(String dataSourceId);
}