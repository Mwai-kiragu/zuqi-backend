package com.zuqi.ai.agent.tools;

import com.zuqi.domain.payment.Payment;
import com.zuqi.domain.payment.PaymentStatus;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.PaymentRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentPerformanceTool {

    private final PaymentRepository paymentRepository;
    private final OrderRepository   orderRepository;

    @Tool("Get payment performance summary for a distributor. Returns totalPayments (all payments on record), " +
          "unreconciledPayments (payments not yet matched to a bank statement), " +
          "completedPayments, failedPayments, overdueOrders (orders whose payment due date has passed " +
          "and are not fully paid), and totalOutstandingAmount (sum of unpaid balances). " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getPaymentPerformance(@P("The distributor UUID") String distributorId) {
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            // Use a large page to capture recent payments (page size 500 as proxy)
            Page<Payment> paymentsPage = paymentRepository.findByDistributorId(
                    distId, PageRequest.of(0, 500));
            long totalPayments = paymentsPage.getTotalElements();

            // Unreconciled count
            long unreconciledPayments = paymentRepository.countUnreconciledPayments(distId);

            // Tally by status from the page content
            List<Payment> payments = paymentsPage.getContent();
            long completedPayments = payments.stream()
                    .filter(p -> PaymentStatus.COMPLETED == p.getStatus())
                    .count();
            long pendingPayments   = payments.stream()
                    .filter(p -> PaymentStatus.PENDING == p.getStatus())
                    .count();
            long failedPayments    = payments.stream()
                    .filter(p -> PaymentStatus.FAILED == p.getStatus())
                    .count();

            // Overdue orders: orders where payment due date <= today and not fully paid
            List<?> overdueOrders = orderRepository.findOverdueOrders(LocalDate.now());
            long overdueOrderCount = overdueOrders.size();

            // Outstanding amount across the distributor
            BigDecimal outstandingAmount = orderRepository.sumOutstandingAmount(distId);
            if (outstandingAmount == null) {
                outstandingAmount = BigDecimal.ZERO;
            }

            return String.format(
                    "{ \"tool\": \"PaymentPerformance\", \"distributorId\": \"%s\", " +
                    "\"totalPayments\": %d, \"unreconciledPayments\": %d, " +
                    "\"completedPayments\": %d, \"pendingPayments\": %d, \"failedPayments\": %d, " +
                    "\"overdueOrders\": %d, \"totalOutstandingAmount\": \"%s\" }",
                    distId,
                    totalPayments, unreconciledPayments,
                    completedPayments, pendingPayments, failedPayments,
                    overdueOrderCount, outstandingAmount.toPlainString()
            );

        } catch (IllegalArgumentException e) {
            log.error("PaymentPerformanceTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("PaymentPerformanceTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve payment performance: " + e.getMessage() + "\" }";
        }
    }
}
