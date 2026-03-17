package com.zuqi.ai.agent.tools;

import com.zuqi.domain.expense.Expense;
import com.zuqi.domain.expense.ExpenseStatus;
import com.zuqi.repository.ExpenseRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class ExpensesTool {

    private final ExpenseRepository expenseRepository;

    @Tool("Get expense summary for a distributor. Returns count and total amount (KES) per status " +
         "(DRAFT, SUBMITTED, APPROVED, REJECTED, PAID), and the top 5 submitted expenses " +
         "awaiting approval with title, amount, and date. " +
         "Use for questions about expenses, spending, expense approvals, cost management.")
    @Transactional(readOnly = true)
    public String getExpenseSummary(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getExpenseSummary distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            long total     = expenseRepository.findByDistributorIdOrderByExpenseDateDesc(distId, PageRequest.of(0, 1)).getTotalElements();
            long draft     = expenseRepository.findByDistributorIdAndStatusOrderByExpenseDateDesc(distId, ExpenseStatus.DRAFT,     PageRequest.of(0, 1)).getTotalElements();
            long submitted = expenseRepository.findByDistributorIdAndStatusOrderByExpenseDateDesc(distId, ExpenseStatus.SUBMITTED, PageRequest.of(0, 1)).getTotalElements();
            long approved  = expenseRepository.findByDistributorIdAndStatusOrderByExpenseDateDesc(distId, ExpenseStatus.APPROVED,  PageRequest.of(0, 1)).getTotalElements();
            long rejected  = expenseRepository.findByDistributorIdAndStatusOrderByExpenseDateDesc(distId, ExpenseStatus.REJECTED,  PageRequest.of(0, 1)).getTotalElements();
            long paid      = expenseRepository.findByDistributorIdAndStatusOrderByExpenseDateDesc(distId, ExpenseStatus.PAID,      PageRequest.of(0, 1)).getTotalElements();

            LocalDate from = LocalDate.now().minusDays(30);
            LocalDate to   = LocalDate.now();
            BigDecimal last30daysAmount = expenseRepository.sumApprovedByDistributorAndDateRange(distId, from, to);
            BigDecimal unpaidApproved   = expenseRepository.sumApprovedUnpaidByDistributor(distId);

            // Top 5 submitted (pending approval) expenses
            List<Expense> submittedExpenses = expenseRepository
                    .findByDistributorIdAndStatusOrderByExpenseDateDesc(distId, ExpenseStatus.SUBMITTED, PageRequest.of(0, 5))
                    .getContent();

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                "{ \"tool\": \"ExpensesSummary\", \"distributorId\": \"%s\", " +
                "\"totalExpenses\": %d, \"draft\": %d, \"submitted\": %d, \"approved\": %d, " +
                "\"rejected\": %d, \"paid\": %d, " +
                "\"last30DaysApprovedPaidKES\": \"%s\", \"unpaidApprovedKES\": \"%s\", ",
                distId, total, draft, submitted, approved, rejected, paid,
                last30daysAmount.toPlainString(), unpaidApproved.toPlainString()));

            sb.append("\"submittedExpenses\": [");
            for (int i = 0; i < submittedExpenses.size(); i++) {
                Expense e = submittedExpenses.get(i);
                String title  = e.getTitle() != null ? e.getTitle().replace("\"", "'") : "N/A";
                String amount = e.getAmount() != null ? e.getAmount().toPlainString() : "0";
                String date   = e.getExpenseDate() != null ? e.getExpenseDate().toString() : "unknown";
                sb.append(String.format(
                        "{ \"title\": \"%s\", \"amountKES\": \"%s\", \"expenseDate\": \"%s\" }",
                        title, amount, date));
                if (i < submittedExpenses.size() - 1) sb.append(", ");
            }
            sb.append("] }");

            return sb.toString();
        } catch (Exception e) {
            log.error("ExpensesTool: error for distributorId '{}': {}", distributorId, e.getMessage());
            return "{ \"error\": \"Failed to retrieve expenses summary: " + e.getMessage() + "\" }";
        }
    }
}
