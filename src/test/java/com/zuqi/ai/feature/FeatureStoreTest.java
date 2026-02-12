package com.zuqi.ai.feature;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.MerchantRepository;
import com.zuqi.repository.ProductRepository;
import com.zuqi.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureStoreTest {

    @Mock
    private MerchantFeatureService merchantFeatureService;

    @Mock
    private OrderFeatureService orderFeatureService;

    @Mock
    private PaymentFeatureService paymentFeatureService;

    @Mock
    private InventoryFeatureService inventoryFeatureService;

    @Mock
    private SalesRepFeatureService salesRepFeatureService;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private FeatureStoreImpl featureStore;

    private UUID distributorId;
    private UUID merchantId;
    private UUID productId;
    private UUID warehouseId;
    private UUID salesRepId;
    private UUID paymentId;

    @BeforeEach
    void setUp() {
        distributorId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        productId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        salesRepId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
    }

    @Test
    void shouldGetMerchantFeatures() {
        // Given
        MerchantFeatures mockFeatures = MerchantFeatures.builder()
                .merchantId(merchantId)
                .build();

        when(merchantFeatureService.computeFeatures(merchantId)).thenReturn(mockFeatures);

        // When
        MerchantFeatures result = featureStore.getMerchantFeatures(merchantId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.merchantId()).isEqualTo(merchantId);
        verify(merchantFeatureService).computeFeatures(merchantId);
    }

    @Test
    void shouldGetDemandFeatures() {
        // Given
        DemandFeatures mockFeatures = DemandFeatures.builder()
                .merchantId(merchantId)
                .productId(productId)
                .build();

        when(orderFeatureService.computeFeatures(merchantId, productId)).thenReturn(mockFeatures);

        // When
        DemandFeatures result = featureStore.getDemandFeatures(merchantId, productId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.merchantId()).isEqualTo(merchantId);
        assertThat(result.productId()).isEqualTo(productId);
        verify(orderFeatureService).computeFeatures(merchantId, productId);
    }

    @Test
    void shouldGetPaymentFeatures() {
        // Given
        PaymentFeatures mockFeatures = PaymentFeatures.builder()
                .paymentId(paymentId)
                .build();

        when(paymentFeatureService.computePaymentFeatures(paymentId)).thenReturn(mockFeatures);

        // When
        PaymentFeatures result = featureStore.getPaymentFeatures(paymentId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.paymentId()).isEqualTo(paymentId);
        verify(paymentFeatureService).computePaymentFeatures(paymentId);
    }

    @Test
    void shouldGetMerchantPaymentTrendFeatures() {
        // Given
        MerchantPaymentTrendFeatures mockFeatures = MerchantPaymentTrendFeatures.builder()
                .merchantId(merchantId)
                .build();

        when(paymentFeatureService.computeMerchantTrendFeatures(merchantId)).thenReturn(mockFeatures);

        // When
        MerchantPaymentTrendFeatures result = featureStore.getMerchantPaymentTrendFeatures(merchantId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.merchantId()).isEqualTo(merchantId);
        verify(paymentFeatureService).computeMerchantTrendFeatures(merchantId);
    }

    @Test
    void shouldGetInventoryFeatures() {
        // Given
        InventoryFeatures mockFeatures = InventoryFeatures.builder()
                .warehouseId(warehouseId)
                .productId(productId)
                .build();

        when(inventoryFeatureService.computeFeatures(warehouseId, productId)).thenReturn(mockFeatures);

        // When
        InventoryFeatures result = featureStore.getInventoryFeatures(warehouseId, productId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.warehouseId()).isEqualTo(warehouseId);
        assertThat(result.productId()).isEqualTo(productId);
        verify(inventoryFeatureService).computeFeatures(warehouseId, productId);
    }

    @Test
    void shouldGetSalesRepFeatures() {
        // Given
        LocalDateTime periodStart = LocalDateTime.of(2026, 2, 1, 0, 0);
        LocalDateTime periodEnd = LocalDateTime.of(2026, 2, 28, 23, 59);

        SalesRepFeatures mockFeatures = SalesRepFeatures.builder()
                .salesRepId(salesRepId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .build();

        when(salesRepFeatureService.computeFeatures(salesRepId, periodStart, periodEnd)).thenReturn(mockFeatures);

        // When
        SalesRepFeatures result = featureStore.getSalesRepFeatures(salesRepId, periodStart, periodEnd);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.salesRepId()).isEqualTo(salesRepId);
        verify(salesRepFeatureService).computeFeatures(salesRepId, periodStart, periodEnd);
    }

    @Test
    void shouldGetAllMerchantFeatures() {
        // Given
        Distributor distributor = Distributor.builder().id(distributorId).build();
        List<Merchant> merchants = createMerchants(3, distributor);

        MerchantFeatures mockFeatures = MerchantFeatures.builder().merchantId(merchantId).build();

        when(merchantRepository.findAll()).thenReturn(merchants);
        when(merchantFeatureService.computeFeatures(any(UUID.class))).thenReturn(mockFeatures);

        // When
        List<MerchantFeatures> result = featureStore.getAllMerchantFeatures(distributorId);

        // Then
        assertThat(result).hasSize(3);
        verify(merchantFeatureService, times(3)).computeFeatures(any(UUID.class));
    }

    @Test
    void shouldGetAllDemandFeatures() {
        // Given
        Distributor distributor = Distributor.builder().id(distributorId).build();
        List<Merchant> merchants = createMerchants(2, distributor);
        List<Product> products = createProducts(2, distributor);

        DemandFeatures mockFeatures = DemandFeatures.builder()
                .merchantId(merchantId)
                .productId(productId)
                .build();

        when(merchantRepository.findAll()).thenReturn(merchants);
        when(productRepository.findAll()).thenReturn(products);
        when(orderFeatureService.computeFeatures(any(UUID.class), any(UUID.class))).thenReturn(mockFeatures);

        // When
        List<DemandFeatures> result = featureStore.getAllDemandFeatures(distributorId);

        // Then
        assertThat(result).hasSize(4); // 2 merchants * 2 products
        verify(orderFeatureService, times(4)).computeFeatures(any(UUID.class), any(UUID.class));
    }

    @Test
    void shouldGetAllInventoryFeatures() {
        // Given
        Distributor distributor = Distributor.builder().id(distributorId).build();
        Warehouse warehouse = Warehouse.builder().id(warehouseId).distributor(distributor).build();
        List<Stock> stocks = createStocks(3, warehouse);

        InventoryFeatures mockFeatures = InventoryFeatures.builder()
                .warehouseId(warehouseId)
                .productId(productId)
                .build();

        when(stockRepository.findAll()).thenReturn(stocks);
        when(inventoryFeatureService.computeFeatures(any(UUID.class), any(UUID.class))).thenReturn(mockFeatures);

        // When
        List<InventoryFeatures> result = featureStore.getAllInventoryFeatures(distributorId);

        // Then
        assertThat(result).hasSize(3);
        verify(inventoryFeatureService, times(3)).computeFeatures(any(UUID.class), any(UUID.class));
    }

    @Test
    void shouldInvalidateMerchantCache() {
        // When
        featureStore.invalidateMerchantCache(merchantId);

        // Then
        verify(merchantFeatureService).evictCache(merchantId);
        verify(orderFeatureService).evictMerchantCache(merchantId);
        verify(paymentFeatureService).evictMerchantTrendCache(merchantId);
    }

    @Test
    void shouldInvalidateWarehouseCache() {
        // When
        featureStore.invalidateWarehouseCache(warehouseId);

        // Then
        verify(inventoryFeatureService).evictWarehouseCache(warehouseId);
    }

    @Test
    void shouldInvalidateSalesRepCache() {
        // When
        featureStore.invalidateSalesRepCache(salesRepId);

        // Then
        verify(salesRepFeatureService).evictRepCache(salesRepId);
    }

    @Test
    void shouldRefreshAllMerchantFeatures() {
        // Given
        Distributor distributor = Distributor.builder().id(distributorId).build();
        List<Merchant> merchants = createMerchants(3, distributor);

        MerchantFeatures mockFeatures = MerchantFeatures.builder().merchantId(merchantId).build();

        when(merchantRepository.findAll()).thenReturn(merchants);
        when(merchantFeatureService.computeFeatures(any(UUID.class))).thenReturn(mockFeatures);

        // When
        featureStore.refreshAllMerchantFeatures(distributorId);

        // Then
        verify(merchantFeatureService, times(3)).evictCache(any(UUID.class));
        verify(merchantFeatureService, times(3)).computeFeatures(any(UUID.class));
    }

    @Test
    void shouldWarmUpCache() {
        // Given
        Distributor distributor = Distributor.builder().id(distributorId).build();
        List<Merchant> merchants = createMerchants(5, distributor);

        MerchantFeatures mockFeatures = MerchantFeatures.builder().merchantId(merchantId).build();

        when(merchantRepository.findAll()).thenReturn(merchants);
        when(merchantFeatureService.computeFeatures(any(UUID.class))).thenReturn(mockFeatures);

        // When
        featureStore.warmUpCache(distributorId);

        // Then
        verify(merchantFeatureService, times(5)).computeFeatures(any(UUID.class));
    }

    @Test
    void shouldHandleExceptionsDuringBulkRetrieval() {
        // Given
        Distributor distributor = Distributor.builder().id(distributorId).build();
        List<Merchant> merchants = createMerchants(3, distributor);

        when(merchantRepository.findAll()).thenReturn(merchants);
        when(merchantFeatureService.computeFeatures(any(UUID.class)))
                .thenThrow(new RuntimeException("Test exception"))
                .thenReturn(MerchantFeatures.builder().merchantId(merchantId).build())
                .thenThrow(new RuntimeException("Test exception"));

        // When
        List<MerchantFeatures> result = featureStore.getAllMerchantFeatures(distributorId);

        // Then
        assertThat(result).hasSize(1); // Only successful computation
        verify(merchantFeatureService, times(3)).computeFeatures(any(UUID.class));
    }

    // ==================== Helper Methods ====================

    private List<Merchant> createMerchants(int count, Distributor distributor) {
        List<Merchant> merchants = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            merchants.add(Merchant.builder()
                    .id(UUID.randomUUID())
                    .businessName("Merchant " + i)
                    .phone("+254700000" + i)
                    .distributor(distributor)
                    .active(true)
                    .build());
        }
        return merchants;
    }

    private List<Product> createProducts(int count, Distributor distributor) {
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            products.add(Product.builder()
                    .id(UUID.randomUUID())
                    .name("Product " + i)
                    .sku("SKU" + i)
                    .distributor(distributor)
                    .active(true)
                    .unitPrice(BigDecimal.valueOf(1000))
                    .build());
        }
        return products;
    }

    private List<Stock> createStocks(int count, Warehouse warehouse) {
        List<Stock> stocks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Product product = Product.builder()
                    .id(UUID.randomUUID())
                    .name("Product " + i)
                    .build();

            stocks.add(Stock.builder()
                    .id(UUID.randomUUID())
                    .warehouse(warehouse)
                    .product(product)
                    .quantity(BigDecimal.valueOf(100))
                    .build());
        }
        return stocks;
    }
}
