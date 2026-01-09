package com.zuqi.repository;

import com.zuqi.domain.user.Role;
import com.zuqi.domain.user.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    default Optional<Role> findByName(RoleName name) {
        return findByName(name.name());
    }

    boolean existsByName(String name);

    default boolean existsByName(RoleName name) {
        return existsByName(name.name());
    }

    List<Role> findBySystemRoleTrue();

    List<Role> findBySystemRoleFalse();
}
