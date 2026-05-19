package com.zuqi.repository;

import com.zuqi.domain.ncba.NcbaConfig;
import com.zuqi.domain.ncba.NcbaConfigStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NcbaConfigRepository extends JpaRepository<NcbaConfig, UUID> {

    List<NcbaConfig> findByMerchantId(UUID merchantId);

    List<NcbaConfig> findByMerchantIdAndStatus(UUID merchantId, NcbaConfigStatus status);

    Optional<NcbaConfig> findByLookupId(String lookupId);
}
