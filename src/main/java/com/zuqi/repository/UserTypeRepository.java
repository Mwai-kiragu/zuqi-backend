package com.zuqi.repository;

import com.zuqi.domain.accesscontrol.UserType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserTypeRepository extends JpaRepository<UserType, UUID> {

    Page<UserType> findByDistributorId(UUID distributorId, Pageable pageable);

    boolean existsByDistributorIdAndName(UUID distributorId, String name);

    @Query("SELECT ut FROM UserType ut LEFT JOIN FETCH ut.permissions WHERE ut.id = :id")
    java.util.Optional<UserType> findByIdWithPermissions(@Param("id") UUID id);
}
