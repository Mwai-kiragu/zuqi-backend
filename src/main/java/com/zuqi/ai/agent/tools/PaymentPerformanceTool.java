package com.zuqi.ai.agent.tools;

import com.zuqi.domain.payment.Payment;
import com.zuqi.domain.payment.PaymentStatus;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.PaymentRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentPerformanceTool {

    private final PaymentRepository paymentRepository;
    private final OrderRepository   orderRepository;

    @Tool("Get payment performance summary for a distributor. Returns totalPayments, " +
          "unreconciledPayments, completedPayments, pendingPayments, failedPayments, " +
          "overdueOrders, totalOutstandingAmount, and the top 5 unreconciled payments " +
          "with merchant names and amounts. " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getPaymentPerformance(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getPaymentPerformance distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            List<Payment> payments = paymentRepository
                    .findByDistributorId(distId, PageRequest.of(0, 500))
                    .getContent();
            long totalPayments        = paymentRepository.findByDistributorId(distId, PageRequest.of(0, 1)).getTotalElements();
            long unreconciledPayments = paymentRepository.countUnreconciledPayments(distId);

            long completedPayments = payments.stream().filter(p -> PaymentStatus.COMPLETED == p.getStatus()).count();
            long pendingPayments   = payments.stream().filter(p -> PaymentStatus.PENDING   == p.getStatus()).count();
            long failedPayments    = payments.stream().filter(p -> PaymentStatus.FAILED    == p.getStatus()).count();

            List<?> overdueOrders = orderRepository.findOverdueOrders(LocalDate.now());
            long overdueOrderCount = overdueOrders.size();

            BigDecimal outstandingAmount = orderRepository.sumOutstandingAmount(distId);
            if (outstandingAmount == null) outstandingAmount = BigDecimal.ZERO;

            // Top 5 unreconciled payments — merchant name + amount
            List<Payment> top5Unreconciled = payments.stream()
                    .filter(p -> !p.isReconciled() && p.getMerchant() != null && p.getAmount() != null)
                    .sorted(Comparator.comparing(Payment::getAmount).reversed())
                    .limit(5)
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                    "{ \"tool\": \"PaymentPerformance\", \"distributorId\": \"%s\", " +
                    "\"totalPayments\": %d, \"unreconciledPayments\": %d, " +
                    "\"completedPayments\": %d, \"pendingPayments\": %d, \"failedPayments\": %d, " +
                    "\"overdueOrders\": %d, \"totalOutstandingAmount\": \"%s\", ",
                    distId, totalPayments, unreconciledPayments,
                    completedPayments, pendingPayments, failedPayments,
                    overdueOrderCount, outstandingAmount.toPlainString()));

            sb.append("\"unreconciledDetails\": [");
            for (int i = 0; i < top5Unreconciled.size(); i++) {
                Payment p = top5Unreconciled.get(i);
                String merchant = p.getMerchant().getBusinessName().replace("\"", "'");
                String date = p.getPaymentDate() != null ? p.getPaymentDate().toLocalDate().toString() : "unknown";
                sb.append(String.format(
                        "{ \"merchant\": \"%s\", \"amountKES\": \"%s\", \"paymentDate\": \"%s\", \"status\": \"%s\" }",
                        merchant, p.getAmount().toPlainString(), date, p.getStatus().name()));
                if (i < top5Unreconciled.size() - 1) sb.append(", ");
            }
            sb.append("] }");

            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("PaymentPerformanceTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("PaymentPerformanceTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve payment performance: " + e.getMessage() + "\" }";
        }
    }
}
