package com.zuqi.repository;

import com.zuqi.domain.pos.PosSalePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PosSalePaymentRepository extends JpaRepository<PosSalePayment, UUID> {

    List<PosSalePayment> findBySaleId(UUID saleId);

    @Query("SELECT p FROM PosSalePayment p WHERE p.sale.shift.id = :shiftId")
    List<PosSalePayment> findByShiftId(@Param("shiftId") UUID shiftId);
}
