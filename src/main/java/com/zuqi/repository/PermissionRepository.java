package com.zuqi.repository;

import com.zuqi.domain.user.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Permission entity operations.
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    /**
     * Find a permission by its name.
     *
     * @param name the permission name
     * @return an Optional containing the permission if found
     */
    Optional<Permission> findByName(String name);

    /**
     * Check if a permission exists with the given name.
     *
     * @param name the permission name
     * @return true if a permission exists with this name
     */
    boolean existsByName(String name);

    /**
     * Find all permissions for a specific module.
     *
     * @param module the module name
     * @return list of permissions for the module
     */
    List<Permission> findByModule(String module);

    /**
     * Find all distinct modules.
     */
    @Query("SELECT DISTINCT p.module FROM Permission p WHERE p.module IS NOT NULL ORDER BY p.module")
    List<String> findDistinctModules();
}
