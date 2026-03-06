package com.zuqi.ai.feature;

import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.ProductRepository;
import com.zuqi.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of FeatureStore providing centralized feature access.
 *
 * Delegates to individual feature services and provides bulk operations and cache management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureStoreImpl implements FeatureStore {

    private final MerchantFeatureService merchantFeatureService;
    private final OrderFeatureService orderFeatureService;
    private final PaymentFeatureService paymentFeatureService;
    private final InventoryFeatureService inventoryFeatureService;
    private final SalesRepFeatureService salesRepFeatureService;

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;

    // ==================== Individual Feature Retrieval ====================

    @Override
    public MerchantFeatures getMerchantFeatures(UUID merchantId) {
        log.debug("Getting merchant features for merchant {}", merchantId);
        return merchantFeatureService.computeFeatures(merchantId);
    }

    @Override
    public DemandFeatures getDemandFeatures(UUID merchantId, UUID productId) {
        log.debug("Getting demand features for merchant {} product {}", merchantId, productId);
        return orderFeatureService.computeFeatures(merchantId, productId);
    }

    @Override
    public PaymentFeatures getPaymentFeatures(UUID paymentId) {
        log.debug("Getting payment features for payment {}", paymentId);
        return paymentFeatureService.computePaymentFeatures(paymentId);
    }

    @Override
    public MerchantPaymentTrendFeatures getMerchantPaymentTrendFeatures(UUID merchantId) {
        log.debug("Getting merchant payment trend features for merchant {}", merchantId);
        return paymentFeatureService.computeMerchantTrendFeatures(merchantId);
    }

    @Override
    public InventoryFeatures getInventoryFeatures(UUID warehouseId, UUID productId) {
        log.debug("Getting inventory features for warehouse {} product {}", warehouseId, productId);
        return inventoryFeatureService.computeFeatures(warehouseId, productId);
    }

    @Override
    public SalesRepFeatures getSalesRepFeatures(UUID salesRepId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        log.debug("Getting sales rep features for rep {} period {}-{}", salesRepId, periodStart, periodEnd);
        return salesRepFeatureService.computeFeatures(salesRepId, periodStart, periodEnd);
    }

    // ==================== Bulk Retrieval Methods ====================

    @Override
    public List<MerchantFeatures> getAllMerchantFeatures(UUID distributorId) {
        log.info("Getting all merchant features for distributor {}", distributorId);

        List<Customer> merchants = customerRepository.findAll().stream()
                .filter(m -> m.getDistributor() != null && m.getDistributor().getId().equals(distributorId))
                .filter(Customer::isActive)
                .collect(Collectors.toList());

        List<MerchantFeatures> features = new ArrayList<>();
        for (Customer merchant : merchants) {
            try {
                features.add(merchantFeatureService.computeFeatures(merchant.getId()));
            } catch (Exception e) {
                log.error("Failed to compute features for merchant {}", merchant.getId(), e);
            }
        }

        log.info("Retrieved {} merchant features for distributor {}", features.size(), distributorId);
        return features;
    }

    @Override
    public List<DemandFeatures> getAllDemandFeatures(UUID distributorId) {
        log.info("Getting all demand features for distributor {}", distributorId);

        List<Customer> merchants = customerRepository.findAll().stream()
                .filter(m -> m.getDistributor() != null && m.getDistributor().getId().equals(distributorId))
                .filter(Customer::isActive)
                .collect(Collectors.toList());

        List<Product> products = productRepository.findAll().stream()
                .filter(p -> p.getDistributor() != null && p.getDistributor().getId().equals(distributorId))
                .filter(Product::isActive)
                .collect(Collectors.toList());

        List<DemandFeatures> features = new ArrayList<>();
        for (Customer merchant : merchants) {
            for (Product product : products) {
                try {
                    features.add(orderFeatureService.computeFeatures(merchant.getId(), product.getId()));
                } catch (Exception e) {
                    log.error("Failed to compute demand features for merchant {} product {}",
                            merchant.getId(), product.getId(), e);
                }
            }
        }

        log.info("Retrieved {} demand features for distributor {}", features.size(), distributorId);
        return features;
    }

    @Override
    public List<InventoryFeatures> getAllInventoryFeatures(UUID distributorId) {
        log.info("Getting all inventory features for distributor {}", distributorId);

        List<Stock> stocks = stockRepository.findAll().stream()
                .filter(s -> s.getWarehouse() != null && s.getWarehouse().getDistributor() != null)
                .filter(s -> s.getWarehouse().getDistributor().getId().equals(distributorId))
                .collect(Collectors.toList());

        List<InventoryFeatures> features = new ArrayList<>();
        for (Stock stock : stocks) {
            try {
                features.add(inventoryFeatureService.computeFeatures(
                        stock.getWarehouse().getId(),
                        stock.getProduct().getId()
                ));
            } catch (Exception e) {
                log.error("Failed to compute inventory features for warehouse {} product {}",
                        stock.getWarehouse().getId(), stock.getProduct().getId(), e);
            }
        }

        log.info("Retrieved {} inventory features for distributor {}", features.size(), distributorId);
        return features;
    }

    // ==================== Cache Management ====================

    @Override
    public void invalidateMerchantCache(UUID merchantId) {
        log.info("Invalidating cache for merchant {}", merchantId);
        merchantFeatureService.evictCache(merchantId);
        orderFeatureService.evictMerchantCache(merchantId);
        paymentFeatureService.evictMerchantTrendCache(merchantId);
    }

    @Override
    public void invalidateWarehouseCache(UUID warehouseId) {
        log.info("Invalidating cache for warehouse {}", warehouseId);
        inventoryFeatureService.evictWarehouseCache(warehouseId);
    }

    @Override
    public void invalidateSalesRepCache(UUID salesRepId) {
        log.info("Invalidating cache for sales rep {}", salesRepId);
        salesRepFeatureService.evictRepCache(salesRepId);
    }

    @Override
    public void refreshAllMerchantFeatures(UUID distributorId) {
        log.info("Refreshing all merchant features for distributor {}", distributorId);

        List<Customer> merchants = customerRepository.findAll().stream()
                .filter(m -> m.getDistributor() != null && m.getDistributor().getId().equals(distributorId))
                .filter(Customer::isActive)
                .collect(Collectors.toList());

        int refreshed = 0;
        for (Customer merchant : merchants) {
            try {
                // Evict cache
                invalidateMerchantCache(merchant.getId());
                // Recompute
                merchantFeatureService.computeFeatures(merchant.getId());
                refreshed++;
            } catch (Exception e) {
                log.error("Failed to refresh features for merchant {}", merchant.getId(), e);
            }
        }

        log.info("Refreshed {} merchant features for distributor {}", refreshed, distributorId);
    }

    @Override
    public void warmUpCache(UUID distributorId) {
        log.info("Warming up cache for distributor {}", distributorId);

        // Warm up merchant features
        List<Customer> merchants = customerRepository.findAll().stream()
                .filter(m -> m.getDistributor() != null && m.getDistributor().getId().equals(distributorId))
                .filter(Customer::isActive)
                .limit(100) // Limit to prevent overwhelming the system
                .collect(Collectors.toList());

        int warmedUp = 0;
        for (Customer merchant : merchants) {
            try {
                merchantFeatureService.computeFeatures(merchant.getId());
                warmedUp++;
            } catch (Exception e) {
                log.error("Failed to warm up features for merchant {}", merchant.getId(), e);
            }
        }

        log.info("Warmed up {} merchant features for distributor {}", warmedUp, distributorId);
    }
}
