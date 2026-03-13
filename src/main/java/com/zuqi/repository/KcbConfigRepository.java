package com.zuqi.repository;

import com.zuqi.domain.kcb.KcbConfig;
import com.zuqi.domain.kcb.KcbConfigStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KcbConfigRepository extends JpaRepository<KcbConfig, UUID> {

    List<KcbConfig> findByMerchantId(UUID merchantId);

    List<KcbConfig> findByMerchantIdAndStatus(UUID merchantId, KcbConfigStatus status);

    boolean existsByMerchantIdAndStatus(UUID merchantId, KcbConfigStatus status);
}
