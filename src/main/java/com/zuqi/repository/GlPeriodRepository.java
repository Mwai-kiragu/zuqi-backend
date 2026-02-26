package com.zuqi.repository;

import com.zuqi.domain.gl.GlPeriod;
import com.zuqi.domain.gl.GlPeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GlPeriodRepository extends JpaRepository<GlPeriod, UUID> {

    List<GlPeriod> findByDistributorIdOrderByPeriodYearDescPeriodMonthDesc(UUID distributorId);

    List<GlPeriod> findByDistributorIdAndStatus(UUID distributorId, GlPeriodStatus status);

    Optional<GlPeriod> findByDistributorIdAndPeriodYearAndPeriodMonth(UUID distributorId, int periodYear, int periodMonth);
}
