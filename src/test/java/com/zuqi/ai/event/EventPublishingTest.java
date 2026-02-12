package com.zuqi.ai.event;

import com.zuqi.ai.event.handler.*;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.StockMovement;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderItem;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.domain.order.OrderType;
import com.zuqi.domain.payment.Payment;
import com.zuqi.domain.payment.PaymentMethod;
import com.zuqi.domain.payment.PaymentStatus;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.user.User;
import com.zuqi.repository.*;
import com.zuqi.service.InvoiceService;
import com.zuqi.service.impl.InventoryServiceImpl;
import com.zuqi.service.impl.MerchantServiceImpl;
import com.zuqi.service.impl.OrderServiceImpl;
import com.zuqi.service.impl.PaymentServiceImpl;
import com.zuqi.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test verifying that AI events are correctly published by service implementations.
 *
 * Tests that:
 * - PaymentRecordedEvent is published when payments are created or completed
 * - StockAdjustedEvent is published when inventory is adjusted
 * - OrderCreatedEvent is published when orders are created
 * - MerchantCreatedEvent is published when merchants are created
 */
@SpringBootTest
@ActiveProfiles("test")
class EventPublishingTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @MockBean
    private PaymentAnomalyEventHandler paymentEventHandler;

    @MockBean
    private InventoryShrinkageEventHandler inventoryEventHandler;

    @MockBean
    private OrderDataQualityEventHandler orderEventHandler;

    @MockBean
    private MerchantCreditEventHandler merchantEventHandler;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private MerchantRepository merchantRepository;

    @MockBean
    private StockRepository stockRepository;

    @MockBean
    private PaymentMethodRepository paymentMethodRepository;

    @MockBean
    private OrderItemRepository orderItemRepository;

    @MockBean
    private OrderStatusHistoryRepository statusHistoryRepository;

    @MockBean
    private ProductRepository productRepository;

    @MockBean
    private DistributorRepository distributorRepository;

    @MockBean
    private WarehouseRepository warehouseRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private MerchantCategoryRepository categoryRepository;

    @MockBean
    private StockMovementRepository stockMovementRepository;

    @MockBean
    private InvoiceService invoiceService;

    @MockBean
    private SecurityUtils securityUtils;

    @Autowired
    private PaymentServiceImpl paymentService;

    @Autowired
    private InventoryServiceImpl inventoryService;

    @Autowired
    private OrderServiceImpl orderService;

    @Autowired
    private MerchantServiceImpl merchantService;

    private UUID distributorId;
    private UUID merchantId;
    private UUID productId;
    private UUID warehouseId;
    private UUID userId;
    private Distributor distributor;
    private Merchant merchant;
    private Product product;
    private Warehouse warehouse;
    private User user;

    @BeforeEach
    void setUp() {
        distributorId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        productId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        userId = UUID.randomUUID();

        distributor = Distributor.builder()
                .id(distributorId)
                .name("Test Distributor")
                .build();

        merchant = Merchant.builder()
                .id(merchantId)
                .businessName("Test Merchant")
                .phone("+254700000000")
                .distributor(distributor)
                .active(true)
                .build();

        product = Product.builder()
                .id(productId)
                .name("Test Product")
                .sku("SKU001")
                .unitPrice(BigDecimal.valueOf(100))
                .distributor(distributor)
                .active(true)
                .build();

        warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Test Warehouse")
                .code("WH001")
                .distributor(distributor)
                .active(true)
                .build();

        user = User.builder()
                .id(userId)
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .password("password")
                .build();
    }

    @Test
    void shouldPublishPaymentRecordedEventWhenPaymentCreated() {
        // Given
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .paymentNumber("PAY-001")
                .merchant(merchant)
                .distributor(distributor)
                .amount(BigDecimal.valueOf(1000))
                .status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
        when(paymentRepository.findMaxPaymentNumberByPrefix(anyString())).thenReturn(0);

        // When
        paymentService.createPayment(createPaymentRequest());

        // Then
        verify(paymentEventHandler, timeout(1000).times(1))
                .handlePaymentRecorded(any(PaymentRecordedEvent.class));
    }

    @Test
    void shouldPublishPaymentRecordedEventWhenPaymentCompleted() {
        // Given
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .paymentNumber("PAY-002")
                .merchant(merchant)
                .distributor(distributor)
                .amount(BigDecimal.valueOf(1000))
                .status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(paymentRepository.findById(any(UUID.class))).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        // When
        paymentService.updatePaymentStatus(payment.getId(), PaymentStatus.COMPLETED);

        // Then
        verify(paymentEventHandler, timeout(1000).times(1))
                .handlePaymentRecorded(any(PaymentRecordedEvent.class));
    }

    @Test
    void shouldPublishStockAdjustedEventWhenInventoryAdjusted() {
        // Given
        Stock stock = Stock.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .product(product)
                .quantity(BigDecimal.valueOf(100))
                .reservedQuantity(BigDecimal.ZERO)
                .build();

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(stockRepository.findByWarehouseIdAndProductId(warehouseId, productId))
                .thenReturn(Optional.of(stock));
        when(stockRepository.save(any(Stock.class))).thenReturn(stock);
        when(stockMovementRepository.save(any(StockMovement.class))).thenReturn(mock(StockMovement.class));

        // When
        inventoryService.adjustStock(createStockAdjustmentRequest(), userId);

        // Then
        verify(inventoryEventHandler, timeout(1000).times(1))
                .handleStockAdjusted(any(StockAdjustedEvent.class));
    }

    @Test
    void shouldPublishOrderCreatedEventWhenOrderCreated() {
        // Given
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNumber("ORD-001")
                .distributor(distributor)
                .merchant(merchant)
                .salesRep(user)
                .orderType(OrderType.STANDARD)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(1000))
                .createdAt(LocalDateTime.now())
                .build();

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .product(product)
                .quantity(BigDecimal.TEN)
                .unitPrice(BigDecimal.valueOf(100))
                .totalAmount(BigDecimal.valueOf(1000))
                .build();

        order.getItems().add(item);

        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(orderRepository.findMaxOrderNumberByPrefix(anyString())).thenReturn(0);

        // When
        orderService.createOrder(createOrderRequest(), user);

        // Then
        verify(orderEventHandler, timeout(1000).times(1))
                .handleOrderCreated(any(OrderCreatedEvent.class));
    }

    @Test
    void shouldPublishMerchantCreatedEventWhenMerchantCreated() {
        // Given
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(merchantRepository.existsByPhone(anyString())).thenReturn(false);
        when(merchantRepository.save(any(Merchant.class))).thenReturn(merchant);

        // When
        merchantService.createMerchant(createMerchantRequest());

        // Then
        verify(merchantEventHandler, timeout(1000).times(1))
                .handleMerchantCreated(any(MerchantCreatedEvent.class));
    }

    // Helper methods to create request objects

    private com.zuqi.api.dto.payment.PaymentRequest createPaymentRequest() {
        return com.zuqi.api.dto.payment.PaymentRequest.builder()
                .distributorId(distributorId)
                .merchantId(merchantId)
                .amount(BigDecimal.valueOf(1000))
                .currency("KES")
                .build();
    }

    private com.zuqi.api.dto.inventory.StockAdjustmentRequest createStockAdjustmentRequest() {
        return com.zuqi.api.dto.inventory.StockAdjustmentRequest.builder()
                .warehouseId(warehouseId)
                .productId(productId)
                .movementType(com.zuqi.domain.inventory.StockMovement.MovementType.IN)
                .quantity(BigDecimal.TEN)
                .notes("Test adjustment")
                .build();
    }

    private com.zuqi.api.dto.order.OrderRequest createOrderRequest() {
        com.zuqi.api.dto.order.OrderItemRequest itemRequest = com.zuqi.api.dto.order.OrderItemRequest.builder()
                .productId(productId)
                .quantity(BigDecimal.TEN)
                .build();

        return com.zuqi.api.dto.order.OrderRequest.builder()
                .distributorId(distributorId)
                .merchantId(merchantId)
                .salesRepId(userId)
                .items(java.util.List.of(itemRequest))
                .build();
    }

    private com.zuqi.api.dto.merchant.MerchantRequest createMerchantRequest() {
        return com.zuqi.api.dto.merchant.MerchantRequest.builder()
                .distributorId(distributorId)
                .businessName("New Merchant")
                .phone("+254700000001")
                .ownerName("Owner Name")
                .address("Test Address")
                .build();
    }
}
