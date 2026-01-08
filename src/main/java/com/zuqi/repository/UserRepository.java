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

/**
 * Repository for User entity operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find a user by email address.
     *
     * @param email the email address
     * @return an Optional containing the user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if a user exists with the given email.
     *
     * @param email the email address
     * @return true if a user exists with this email
     */
    boolean existsByEmail(String email);

    /**
     * Find a user by phone number.
     *
     * @param phoneNumber the phone number
     * @return an Optional containing the user if found
     */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * Check if a user exists with the given phone number.
     *
     * @param phoneNumber the phone number
     * @return true if a user exists with this phone number
     */
    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * Find all active users.
     *
     * @param pageable pagination information
     * @return a page of active users
     */
    Page<User> findByActiveTrue(Pageable pageable);

    /**
     * Find users by distributor ID.
     *
     * @param distributorId the distributor ID
     * @return list of users belonging to the distributor
     */
    List<User> findByDistributorIdAndActiveTrue(UUID distributorId);

    /**
     * Find users by role name.
     *
     * @param roleName the role name
     * @param pageable pagination information
     * @return a page of users with the specified role
     */
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName AND u.active = true")
    Page<User> findByRoleName(@Param("roleName") String roleName, Pageable pageable);

    /**
     * Update last login timestamp.
     *
     * @param userId      the user ID
     * @param lastLoginAt the login timestamp
     */
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :lastLoginAt WHERE u.id = :userId")
    void updateLastLoginAt(@Param("userId") UUID userId, @Param("lastLoginAt") LocalDateTime lastLoginAt);

    /**
     * Soft delete a user by setting active to false.
     *
     * @param userId the user ID
     */
    @Modifying
    @Query("UPDATE User u SET u.active = false WHERE u.id = :userId")
    void softDeleteById(@Param("userId") UUID userId);

    /**
     * Search users by name or email.
     *
     * @param searchTerm the search term
     * @param pageable   pagination information
     * @return a page of matching users
     */
    @Query("SELECT u FROM User u WHERE u.active = true AND " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<User> searchUsers(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Count users by role and distributor.
     */
    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.name = :roleName " +
            "AND u.distributorId = :distributorId AND u.active = true")
    long countByRoleAndDistributor(@Param("roleName") String roleName, @Param("distributorId") UUID distributorId);

    // Global queries for SUPER_ADMIN/ADMIN (no distributor filter)
    /**
     * Count all users by role across all distributors.
     */
    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.name = :roleName AND u.active = true")
    long countByRole(@Param("roleName") String roleName);
}
