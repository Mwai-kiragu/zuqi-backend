package com.zuqi.ai.demand;

import com.zuqi.ai.event.StockAdjustedEvent;
import com.zuqi.domain.inventory.ProductBatch;
import com.zuqi.repository.ProductBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Listens for inbound stock events (new batch received) and triggers
 * expiry risk prediction for batches that have an expiry date set.
 *
 * Blueprint: phase2-plan.md Section 2.2
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockExpiryEventHandler {

    private final ProductBatchRepository productBatchRepository;
    private final ExpiryRiskPredictor expiryRiskPredictor;

    /**
     * When stock is received (INBOUND movement), find all product batches
     * associated with this warehouse/product and score them for expiry risk.
     */
    @Async
    @EventListener
    public void onStockAdjusted(StockAdjustedEvent event) {
        if (!"INBOUND".equals(event.movementType())) {
            return;
        }

        log.debug("StockExpiryEventHandler: INBOUND event for product {} warehouse {}",
                event.productId(), event.warehouseId());

        try {
            List<ProductBatch> batches = productBatchRepository
                    .findByWarehouseIdAndProductId(event.warehouseId(), event.productId());

            for (ProductBatch batch : batches) {
                if (batch.getExpiryDate() == null) {
                    continue; // non-perishable
                }
                try {
                    expiryRiskPredictor.predict(
                            event.distributorId(),
                            event.warehouseId(),
                            event.productId(),
                            batch.getId()
                    );
                } catch (Exception e) {
                    log.warn("Expiry risk prediction failed for batch {}: {}",
                            batch.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("StockExpiryEventHandler failed for product {}: {}",
                    event.productId(), e.getMessage(), e);
        }
    }
}
