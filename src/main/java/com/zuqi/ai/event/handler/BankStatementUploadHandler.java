package com.zuqi.ai.event.handler;

import com.zuqi.ai.recon.BankReconMatcher;
import com.zuqi.ai.recon.BankReconMatcher.MatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Async event handler for bank statement upload events.
 *
 * When finance uploads a bank statement, this handler fires BankReconMatcher
 * for each line so results are available for review without blocking the upload.
 *
 * Listens for {@link BankStatementUploadedEvent}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BankStatementUploadHandler {

    private final BankReconMatcher bankReconMatcher;

    @Async
    @EventListener
    public void handleBankStatementUploaded(BankStatementUploadedEvent event) {
        log.info("Processing bank statement upload: reconciliationId={} lines={}",
                event.reconciliationId(), event.lines().size());

        int autoMatched = 0;
        int suggested = 0;

        for (BankStatementLine line : event.lines()) {
            try {
                List<MatchResult> matches = bankReconMatcher.findMatches(
                        event.distributorId(),
                        line.amount(),
                        line.statementDate(),
                        line.description(),
                        line.reference()
                );

                for (MatchResult match : matches) {
                    if ("AUTO_MATCH".equals(match.disposition())) autoMatched++;
                    else suggested++;
                }
            } catch (Exception e) {
                log.warn("Recon failed for line {}: {}", line.lineId(), e.getMessage());
            }
        }

        log.info("Bank statement processed: {} auto-matched, {} suggested for manual review",
                autoMatched, suggested);
    }

    // ── Event and value types ─────────────────────────────────────────────────

    /**
     * Published when a bank statement is uploaded via the finance module.
     */
    public record BankStatementUploadedEvent(
            UUID reconciliationId,
            UUID distributorId,
            List<BankStatementLine> lines
    ) {}

    public record BankStatementLine(
            UUID lineId,
            BigDecimal amount,
            LocalDate statementDate,
            String description,
            String reference
    ) {}
}
