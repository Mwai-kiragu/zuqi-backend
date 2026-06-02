package com.zuqi.service;

import com.zuqi.domain.inventory.Stock;
import com.zuqi.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LowStockAlertScheduler {

    private final StockRepository stockRepository;
    private final NotificationService notificationService;

    /**
     * Nightly sweep at 21:00 — find all stocks at or below reorder level
     * that haven't been alerted in the past 12 hours and send notifications.
     */
    @Scheduled(cron = "${zuqi.inventory.low-stock-alert-cron:0 0 21 * * *}")
    @Transactional
    public void sendNightlyLowStockAlerts() {
        LocalDateTime alertThreshold = LocalDateTime.now().minusHours(12);
        List<Stock> lowStocks = stockRepository.findLowStockNotRecentlyAlerted(alertThreshold);
        if (lowStocks.isEmpty()) {
            log.info("Nightly low-stock sweep: no stocks below reorder level require alerting.");
            return;
        }
        log.info("Nightly low-stock sweep: {} stock(s) below reorder level — sending alerts.", lowStocks.size());
        for (Stock stock : lowStocks) {
            try {
                notificationService.notifyLowStock(stock);
                stock.setLastLowStockAlertSentAt(LocalDateTime.now());
                stockRepository.save(stock);
            } catch (Exception e) {
                log.error("Failed to send low-stock alert for stock {}: {}", stock.getId(), e.getMessage());
            }
        }
    }
}
