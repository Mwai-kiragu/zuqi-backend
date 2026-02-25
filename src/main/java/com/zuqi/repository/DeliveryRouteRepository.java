package com.zuqi.repository;

import com.zuqi.domain.ai.DeliveryRoute;
import com.zuqi.domain.ai.RouteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryRouteRepository extends JpaRepository<DeliveryRoute, UUID> {

    List<DeliveryRoute> findByDistributorIdAndRouteDate(UUID distributorId, LocalDate routeDate);

    List<DeliveryRoute> findByDistributorIdAndStatus(UUID distributorId, RouteStatus status);

    List<DeliveryRoute> findByDriverIdAndRouteDate(UUID driverId, LocalDate routeDate);

    @Query("SELECT r FROM DeliveryRoute r WHERE r.distributor.id = :distributorId " +
           "AND r.routeDate = :routeDate AND r.status != 'CANCELLED'")
    List<DeliveryRoute> findActiveRoutesForDate(
            @Param("distributorId") UUID distributorId,
            @Param("routeDate") LocalDate routeDate);

    boolean existsByDistributorIdAndRouteDate(UUID distributorId, LocalDate routeDate);
}
