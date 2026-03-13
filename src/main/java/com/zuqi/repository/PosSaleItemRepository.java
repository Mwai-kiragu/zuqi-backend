package com.zuqi.repository;

import com.zuqi.domain.pos.PosSaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PosSaleItemRepository extends JpaRepository<PosSaleItem, UUID> {

    List<PosSaleItem> findBySaleId(UUID saleId);
}
