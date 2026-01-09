package com.zuqi.repository;

import com.zuqi.domain.user.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByName(String name);

    boolean existsByName(String name);

    List<Permission> findByModule(String module);

    @Query("SELECT DISTINCT p.module FROM Permission p WHERE p.module IS NOT NULL ORDER BY p.module")
    List<String> findDistinctModules();
}
