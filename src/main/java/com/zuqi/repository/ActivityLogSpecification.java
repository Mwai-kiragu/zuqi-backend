package com.zuqi.repository;

import com.zuqi.domain.audit.ActivityAction;
import com.zuqi.domain.audit.ActivityLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ActivityLogSpecification {

    private ActivityLogSpecification() {}

    public static Specification<ActivityLog> withFilters(
            UUID userId,
            ActivityAction action,
            String entityType,
            String module,
            LocalDateTime from,
            LocalDateTime to,
            Boolean success,
            List<UUID> allowedDistributorIds,
            Set<UUID> fallbackUserIds) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Tenant isolation: non-null list means we must restrict the result set.
            // New entries (post-V242) carry distributor_id directly.
            // Legacy entries (null distributor_id) fall back to user_id IN (fallbackUserIds).
            if (allowedDistributorIds != null) {
                if (allowedDistributorIds.isEmpty()) {
                    // No accessible distributors → return nothing
                    predicates.add(cb.disjunction());
                } else {
                    Predicate byDistributor = root.get("distributorId").in(allowedDistributorIds);
                    if (fallbackUserIds != null && !fallbackUserIds.isEmpty()) {
                        Predicate nullDistributor = root.get("distributorId").isNull();
                        Predicate byUser = root.get("userId").in(fallbackUserIds);
                        predicates.add(cb.or(
                                byDistributor,
                                cb.and(nullDistributor, byUser)
                        ));
                    } else {
                        predicates.add(byDistributor);
                    }
                }
            }

            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (module != null && !module.isBlank()) {
                predicates.add(cb.equal(root.get("module"), module));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (success != null) {
                predicates.add(cb.equal(root.get("success"), success));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
