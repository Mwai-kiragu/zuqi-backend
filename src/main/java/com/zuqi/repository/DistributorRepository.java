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

@Repository
public interface DistributorRepository extends JpaRepository<Distributor, UUID> {

    Page<Distributor> findByActiveTrue(Pageable pageable);

    List<Distributor> findByActiveTrue();

    Page<Distributor> findByActiveFalse(Pageable pageable);

    Optional<Distributor> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT d FROM Distributor d WHERE d.active = true AND " +
            "LOWER(d.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Distributor> searchByName(@Param("searchTerm") String searchTerm, Pageable pageable);
}
