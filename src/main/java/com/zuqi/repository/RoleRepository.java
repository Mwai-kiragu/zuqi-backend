package com.zuqi.repository;

import com.zuqi.domain.user.Role;
import com.zuqi.domain.user.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Role entity operations.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Find a role by its name (String).
     *
     * @param name the role name
     * @return an Optional containing the role if found
     */
    Optional<Role> findByName(String name);

    /**
     * Find a role by its RoleName enum.
     *
     * @param name the role name enum
     * @return an Optional containing the role if found
     */
    default Optional<Role> findByName(RoleName name) {
        return findByName(name.name());
    }

    /**
     * Check if a role exists with the given name.
     *
     * @param name the role name
     * @return true if a role exists with this name
     */
    boolean existsByName(String name);

    /**
     * Check if a role exists with the given RoleName enum.
     *
     * @param name the role name enum
     * @return true if a role exists with this name
     */
    default boolean existsByName(RoleName name) {
        return existsByName(name.name());
    }

    /**
     * Find all system roles.
     */
    List<Role> findBySystemRoleTrue();

    /**
     * Find all custom (non-system) roles.
     */
    List<Role> findBySystemRoleFalse();
}
