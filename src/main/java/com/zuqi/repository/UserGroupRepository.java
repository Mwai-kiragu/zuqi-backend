package com.zuqi.repository;

import com.zuqi.domain.accesscontrol.UserGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserGroupRepository extends JpaRepository<UserGroup, UUID> {

    Page<UserGroup> findByDistributorId(UUID distributorId, Pageable pageable);

    List<UserGroup> findByDistributorId(UUID distributorId);

    boolean existsByDistributorIdAndName(UUID distributorId, String name);

    boolean existsByDistributorIdAndNameAndUserTypeId(UUID distributorId, String name, UUID userTypeId);
}
