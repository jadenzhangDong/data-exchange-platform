package com.dex.common.repository;

import com.dex.common.model.entity.DataSourceMetaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSourceMetaRepository extends JpaRepository<DataSourceMetaEntity, String> {
}