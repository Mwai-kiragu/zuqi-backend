package com.zuqi.repository;

import com.zuqi.domain.pricing.PriceListItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PriceListItemRepository extends JpaRepository<PriceListItem, UUID> {

    @Query(
        value = """
            SELECT i FROM PriceListItem i JOIN FETCH i.product p
            WHERE i.priceList.id = :priceListId
              AND ('' = :search OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """,
        countQuery = """
            SELECT COUNT(i) FROM PriceListItem i JOIN i.product p
            WHERE i.priceList.id = :priceListId
              AND ('' = :search OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """
    )
    Page<PriceListItem> findByPriceListIdAndSearch(
            @Param("priceListId") UUID priceListId,
            @Param("search") String search,
            Pageable pageable);
}
