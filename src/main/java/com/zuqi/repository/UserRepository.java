package com.zuqi.repository;

import com.zuqi.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    Page<User> findByActiveTrue(Pageable pageable);

    Page<User> findByActiveFalse(Pageable pageable);

    Page<User> findByDistributorIdAndActiveFalse(UUID distributorId, Pageable pageable);

    List<User> findByDistributorId(UUID distributorId);

    List<User> findByDistributorIdAndActiveTrue(UUID distributorId);

    List<User> findByDistributorIdAndActiveFalse(UUID distributorId);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName AND u.active = true")
    Page<User> findByRoleName(@Param("roleName") String roleName, Pageable pageable);

    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :lastLoginAt WHERE u.id = :userId")
    void updateLastLoginAt(@Param("userId") UUID userId, @Param("lastLoginAt") LocalDateTime lastLoginAt);

    @Modifying
    @Query("UPDATE User u SET u.active = false WHERE u.id = :userId")
    void softDeleteById(@Param("userId") UUID userId);

    @Query("SELECT u FROM User u WHERE u.active = true AND " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<User> searchUsers(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.name = :roleName " +
            "AND u.distributorId = :distributorId AND u.active = true")
    long countByRoleAndDistributor(@Param("roleName") String roleName, @Param("distributorId") UUID distributorId);

    // Global queries for SUPER_ADMIN/ADMIN (no distributor filter)
    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.name = :roleName AND u.active = true")
    long countByRole(@Param("roleName") String roleName);
}
