package com.zuqi.ai.anomaly;

import com.zuqi.ai.event.OrderCreatedEvent;
import com.zuqi.domain.ai.AlertSeverity;
import com.zuqi.domain.ai.AlertType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tier-1 rules-based data quality detector for incoming orders.
 *
 * Rules checked per order:
 * 1. Order must have at least 1 item
 * 2. Each item: unitPrice != null && unitPrice > 0
 * 3. Each item: quantity > 0
 * 4. Each item: quantity <= 10,000 (suspiciously large threshold)
 * 5. merchantId not null
 * 6. totalAmount consistent with sum of (qty × price) within 1% tolerance
 *
 * If violations are found, an alert is created via AlertService (one per order).
 *
 * Tier-2 ML detection is Phase 6.
 *
 * Blueprint reference: plan.md Section 6.3 - DataQualityDetector (Phase 4)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataQualityDetector {

    private static final int    MAX_ITEM_QUANTITY   = 10_000;
    private static final double TOTAL_AMOUNT_TOLERANCE = 0.01;  // 1%

    private final AlertService alertService;

    /**
     * Validate an order for data quality violations and raise an alert if any are found.
     *
     * @param event The OrderCreatedEvent to validate
     * @return List of violations found (empty if the order is clean)
     */
    public List<DataQualityViolation> detect(OrderCreatedEvent event) {
        List<DataQualityViolation> violations = new ArrayList<>();

        // Rule 1: at least one item
        if (event.items() == null || event.items().isEmpty()) {
            violations.add(new DataQualityViolation(
                    "items", "ORDER_MUST_HAVE_ITEMS",
                    "0", AlertSeverity.HIGH));
        }

        // Rule 5: merchantId not null (already validated in event constructor, but belt-and-braces)
        if (event.merchantId() == null) {
            violations.add(new DataQualityViolation(
                    "merchantId", "MERCHANT_ID_REQUIRED",
                    "null", AlertSeverity.HIGH));
        }

        // Per-item rules
        if (event.items() != null) {
            for (int idx = 0; idx < event.items().size(); idx++) {
                OrderCreatedEvent.OrderItem item = event.items().get(idx);
                String itemRef = "items[" + idx + "]";

                // Rule 2: unit price must be positive
                if (item.unitPrice() == null || item.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    violations.add(new DataQualityViolation(
                            itemRef + ".unitPrice", "POSITIVE_UNIT_PRICE_REQUIRED",
                            String.valueOf(item.unitPrice()), AlertSeverity.MEDIUM));
                }

                // Rule 3: quantity must be positive (already validated in OrderItem constructor, extra check)
                if (item.quantity() == null || item.quantity() <= 0) {
                    violations.add(new DataQualityViolation(
                            itemRef + ".quantity", "POSITIVE_QUANTITY_REQUIRED",
                            String.valueOf(item.quantity()), AlertSeverity.MEDIUM));
                }

                // Rule 4: quantity suspiciously large
                if (item.quantity() != null && item.quantity() > MAX_ITEM_QUANTITY) {
                    violations.add(new DataQualityViolation(
                            itemRef + ".quantity", "QUANTITY_EXCEEDS_THRESHOLD",
                            String.valueOf(item.quantity()), AlertSeverity.MEDIUM));
                }
            }
        }

        // Rule 6: total amount consistency
        if (event.items() != null && !event.items().isEmpty() && event.totalAmount() != null) {
            BigDecimal computedTotal = event.items().stream()
                    .filter(i -> i.unitPrice() != null && i.quantity() != null && i.quantity() > 0)
                    .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (computedTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal diff = event.totalAmount().subtract(computedTotal).abs();
                BigDecimal tolerance = computedTotal.multiply(BigDecimal.valueOf(TOTAL_AMOUNT_TOLERANCE));
                if (diff.compareTo(tolerance) > 0) {
                    violations.add(new DataQualityViolation(
                            "totalAmount", "TOTAL_AMOUNT_INCONSISTENT",
                            "expected=" + computedTotal.setScale(2, RoundingMode.HALF_UP)
                                    + " actual=" + event.totalAmount(),
                            AlertSeverity.MEDIUM));
                }
            }
        }

        // Create a single alert if violations found
        if (!violations.isEmpty()) {
            AlertSeverity maxSeverity = violations.stream()
                    .map(DataQualityViolation::severity)
                    .max(Enum::compareTo)
                    .orElse(AlertSeverity.MEDIUM);

            Map<String, Object> context = buildContext(violations);

            alertService.createAlert(
                    AlertType.DATA_QUALITY,
                    maxSeverity,
                    "ORDER",
                    event.orderId(),
                    event.distributorId(),
                    (double) violations.size(),
                    "Data quality violations detected on order " + event.orderId()
                            + ": " + violations.size() + " violation(s)",
                    context
            );

            log.warn("Data quality violations for order {}: {} violation(s)", event.orderId(), violations.size());
        } else {
            log.debug("Order {} passed data quality checks", event.orderId());
        }

        return violations;
    }

    private Map<String, Object> buildContext(List<DataQualityViolation> violations) {
        Map<String, Object> ctx = new HashMap<>();
        List<Map<String, String>> details = new ArrayList<>();
        for (DataQualityViolation v : violations) {
            details.add(Map.of(
                    "field",        v.field(),
                    "rule",         v.rule(),
                    "actualValue",  v.actualValue() != null ? v.actualValue() : "null",
                    "severity",     v.severity().name()
            ));
        }
        ctx.put("violations", details);
        ctx.put("violationCount", violations.size());
        return ctx;
    }

    // ── Violation record ──────────────────────────────────────────────────

    public record DataQualityViolation(
            String        field,
            String        rule,
            String        actualValue,
            AlertSeverity severity
    ) {}
}
