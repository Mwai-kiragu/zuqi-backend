package com.zuqi.repository;

import com.zuqi.domain.merchant.Merchant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Page<Merchant> findByActiveTrue(Pageable pageable);

    Page<Merchant> findByActiveFalse(Pageable pageable);

    Page<Merchant> findByActive(boolean active, Pageable pageable);

    Optional<Merchant> findByName(String name);

    boolean existsByName(String name);

    boolean existsByEmail(String email);

    @Query("SELECT m FROM Merchant m WHERE " +
           "(LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(m.registrationNumber, '')) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Merchant> search(@Param("search") String search, Pageable pageable);

    @Query("SELECT m FROM Merchant m WHERE m.active = :active AND " +
           "(LOWER(m.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(m.registrationNumber, '')) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Merchant> searchByActive(@Param("search") String search, @Param("active") boolean active, Pageable pageable);
}
