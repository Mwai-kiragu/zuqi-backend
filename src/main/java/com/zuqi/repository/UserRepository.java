package com.zuqi.repository;

import com.zuqi.domain.accesscontrol.UserTypePermission;
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

    Page<User> findByDistributorId(UUID distributorId, Pageable pageable);

    Page<User> findByDistributorIdAndActiveTrue(UUID distributorId, Pageable pageable);

    Page<User> findByDistributorIdAndActiveFalse(UUID distributorId, Pageable pageable);

    List<User> findByDistributorId(UUID distributorId);

    List<User> findByDistributorIdIn(java.util.Collection<UUID> distributorIds);

    List<User> findByDistributorIdAndActiveTrue(UUID distributorId);

    List<User> findByDistributorIdAndActiveFalse(UUID distributorId);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName AND u.active = true")
    Page<User> findByRoleName(@Param("roleName") String roleName, Pageable pageable);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE u.distributorId = :distributorId AND r.name = 'DRIVER' AND u.active = true ORDER BY u.firstName ASC")
    List<User> findActiveDriversByDistributorId(@Param("distributorId") UUID distributorId);

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

    /** Fetch all UserType permissions for a user via their UserGroup → UserType chain. */
    @Query("SELECT p FROM UserType ut JOIN ut.permissions p " +
           "WHERE ut.id = (SELECT u.userGroup.userType.id FROM User u WHERE u.id = :userId " +
           "AND u.userGroup IS NOT NULL AND u.userGroup.userType IS NOT NULL)")
    List<UserTypePermission> findUserTypePermissionsByUserId(@Param("userId") UUID userId);

    /** Scope to a merchant brand (MERCHANT_ADMIN) — users who belong to the brand or its distributors. */
    @Query("SELECT u FROM User u WHERE u.merchantId = :merchantId OR " +
            "u.distributorId IN (SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId)")
    Page<User> findByMerchantScope(@Param("merchantId") UUID merchantId, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.active = false AND (u.merchantId = :merchantId OR " +
            "u.distributorId IN (SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId))")
    Page<User> findInactiveByMerchantScope(@Param("merchantId") UUID merchantId, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.active = true AND (u.merchantId = :merchantId OR " +
            "u.distributorId IN (SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId))")
    List<User> findActiveByMerchantScope(@Param("merchantId") UUID merchantId);

    // ── Search queries (name / email / phone) ──────────────────────────────

    @Query("SELECT u FROM User u WHERE " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchAllUsers(@Param("search") String search, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.active = :active AND " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchAllUsersByActive(@Param("search") String search, @Param("active") boolean active, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.distributorId = :distributorId AND " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchByDistributor(@Param("distributorId") UUID distributorId, @Param("search") String search, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.distributorId = :distributorId AND u.active = :active AND " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchByDistributorAndActive(@Param("distributorId") UUID distributorId, @Param("search") String search, @Param("active") boolean active, Pageable pageable);

    @Query("SELECT u FROM User u WHERE (u.merchantId = :merchantId OR " +
            "u.distributorId IN (SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId)) AND " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchByMerchantScope(@Param("merchantId") UUID merchantId, @Param("search") String search, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.active = false AND (u.merchantId = :merchantId OR " +
            "u.distributorId IN (SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId)) AND " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchInactiveByMerchantScope(@Param("merchantId") UUID merchantId, @Param("search") String search, Pageable pageable);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN u.roles r LEFT JOIN u.userGroup ug " +
            "WHERE u.distributorId = :distributorId AND u.active = true " +
            "AND (r.name IN ('VERIFIER','AUTHORIZER','DISTRIBUTOR_ADMIN') " +
            "     OR ug.workflowTier IN ('VERIFIER','AUTHORIZER'))")
    List<User> findActiveApproversByDistributorId(@Param("distributorId") UUID distributorId);
}
