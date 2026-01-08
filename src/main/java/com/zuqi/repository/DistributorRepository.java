package com.zuqi.repository;

import com.zuqi.domain.distributor.Distributor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Distributor entity operations.
 */
@Repository
public interface DistributorRepository extends JpaRepository<Distributor, UUID> {

    /**
     * Find all active distributors.
     */
    Page<Distributor> findByActiveTrue(Pageable pageable);

    /**
     * Find all active distributors as a list.
     */
    List<Distributor> findByActiveTrue();

    /**
     * Find distributor by name.
     */
    Optional<Distributor> findByName(String name);

    /**
     * Check if distributor exists by name.
     */
    boolean existsByName(String name);

    /**
     * Search distributors by name.
     */
    @Query("SELECT d FROM Distributor d WHERE d.active = true AND " +
            "LOWER(d.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Distributor> searchByName(@Param("searchTerm") String searchTerm, Pageable pageable);
}
