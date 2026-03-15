package com.zuqi.service.impl;

import com.zuqi.api.dto.approval.CreateApprovalRequestDto;
import com.zuqi.api.dto.gl.*;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.domain.gl.*;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.*;
import com.zuqi.service.ApprovalService;
import com.zuqi.service.GlPeriodService;
import com.zuqi.service.JournalEntryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class JournalEntryServiceImpl implements JournalEntryService {

    private final JournalEntryRepository journalEntryRepository;
    private final GlAccountRepository glAccountRepository;
    private final CostCenterRepository costCenterRepository;
    private final GlPeriodService glPeriodService;
    private final ApprovalService approvalService;

    @Override
    public Page<JournalEntryResponse> getAll(UUID distributorId, UUID merchantId, JournalEntryStatus status,
                                              LocalDate fromDate, LocalDate toDate,
                                              JournalSourceModule sourceModule, Pageable pageable) {
        if (merchantId != null) {
            return journalEntryRepository.findByMerchantIdWithFilters(merchantId, status, fromDate, toDate, sourceModule, pageable)
                    .map(JournalEntryResponse::fromEntity);
        }
        return journalEntryRepository.findByFilters(distributorId, status, fromDate, toDate, sourceModule, pageable)
                .map(JournalEntryResponse::fromEntity);
    }

    @Override
    public JournalEntryResponse getById(UUID id) {
        return JournalEntryResponse.fromEntity(findById(id));
    }

    @Override
    @Transactional
    public JournalEntryResponse create(UUID distributorId, JournalEntryRequest request, User currentUser) {
        GlPeriod period = glPeriodService.getOpenPeriodForDate(distributorId, request.getEntryDate());
        validateLines(request.getLines());

        JournalEntry entry = buildEntry(distributorId, request, period, currentUser, JournalEntryStatus.DRAFT);
        populateLines(entry, request.getLines());

        return JournalEntryResponse.fromEntity(journalEntryRepository.save(entry));
    }

    @Override
    @Transactional
    public JournalEntryResponse update(UUID id, JournalEntryRequest request, User currentUser) {
        JournalEntry entry = findById(id);
        if (entry.getStatus() != JournalEntryStatus.DRAFT) {
            throw new ValidationException("Only DRAFT journal entries can be edited");
        }
        GlPeriod period = glPeriodService.getOpenPeriodForDate(entry.getDistributorId(), request.getEntryDate());
        validateLines(request.getLines());

        entry.setEntryDate(request.getEntryDate());
        entry.setDescription(request.getDescription());
        entry.setReference(request.getReference());
        entry.setPeriodId(period.getId());
        entry.getLines().clear();
        populateLines(entry, request.getLines());
        recalculateTotals(entry);

        return JournalEntryResponse.fromEntity(journalEntryRepository.save(entry));
    }

    @Override
    @Transactional
    public JournalEntryResponse submit(UUID id, User currentUser) {
        JournalEntry entry = findById(id);
        if (entry.getStatus() != JournalEntryStatus.DRAFT) {
            throw new ValidationException("Only DRAFT entries can be submitted");
        }
        entry.setStatus(JournalEntryStatus.PENDING_APPROVAL);

        approvalService.createRequest(currentUser.getId(), CreateApprovalRequestDto.builder()
                .workflowType(ApprovalWorkflowType.JOURNAL_ENTRY)
                .entityType("JOURNAL_ENTRY")
                .entityId(entry.getId())
                .entityName(entry.getEntryNumber())
                .description("Journal entry " + entry.getEntryNumber() + " pending approval")
                .requestedValues(java.util.Map.of(
                        "entryNumber", entry.getEntryNumber(),
                        "totalDebit", entry.getTotalDebit(),
                        "entryDate", entry.getEntryDate().toString()
                ))
                .build());

        return JournalEntryResponse.fromEntity(journalEntryRepository.save(entry));
    }

    @Override
    @Transactional
    public JournalEntryResponse approve(UUID id, User currentUser, String comments) {
        JournalEntry entry = findById(id);
        if (entry.getStatus() != JournalEntryStatus.PENDING_APPROVAL) {
            throw new ValidationException("Only PENDING_APPROVAL entries can be approved");
        }
        entry.setStatus(JournalEntryStatus.POSTED);
        entry.setPostedAt(LocalDateTime.now());
        entry.setPostedBy(currentUser.getId());
        return JournalEntryResponse.fromEntity(journalEntryRepository.save(entry));
    }

    @Override
    @Transactional
    public JournalEntryResponse reject(UUID id, User currentUser, String reason) {
        JournalEntry entry = findById(id);
        if (entry.getStatus() != JournalEntryStatus.PENDING_APPROVAL) {
            throw new ValidationException("Only PENDING_APPROVAL entries can be rejected");
        }
        entry.setStatus(JournalEntryStatus.REJECTED);
        entry.setRejectedAt(LocalDateTime.now());
        entry.setRejectionReason(reason);
        return JournalEntryResponse.fromEntity(journalEntryRepository.save(entry));
    }

    @Override
    @Transactional
    public JournalEntryResponse reverse(UUID id, User currentUser) {
        JournalEntry original = findById(id);
        if (original.getStatus() != JournalEntryStatus.POSTED) {
            throw new ValidationException("Only POSTED entries can be reversed");
        }
        if (original.getReversedByEntryId() != null) {
            throw new ValidationException("Entry has already been reversed");
        }

        GlPeriod period = glPeriodService.getOpenPeriodForDate(original.getDistributorId(), LocalDate.now());

        String reversalNumber = generateEntryNumber(original.getDistributorId(), LocalDate.now());
        JournalEntry reversal = JournalEntry.builder()
                .distributorId(original.getDistributorId())
                .entryNumber(reversalNumber)
                .periodId(period.getId())
                .entryDate(LocalDate.now())
                .description("REVERSAL of " + original.getEntryNumber() + ": " + original.getDescription())
                .reference(original.getReference())
                .sourceModule(JournalSourceModule.MANUAL)
                .status(JournalEntryStatus.POSTED)
                .isReversal(true)
                .reversalOfEntryId(original.getId())
                .postedAt(LocalDateTime.now())
                .postedBy(currentUser.getId())
                .createdBy(currentUser.getId())
                .build();

        reversal.setLines(new ArrayList<>());
        int lineNum = 1;
        for (JournalEntryLine origLine : original.getLines()) {
            JournalEntryLine reversalLine = JournalEntryLine.builder()
                    .journalEntry(reversal)
                    .lineNumber(lineNum++)
                    .account(origLine.getAccount())
                    .costCenter(origLine.getCostCenter())
                    .description(origLine.getDescription())
                    .debitAmount(origLine.getCreditAmount())
                    .creditAmount(origLine.getDebitAmount())
                    .reference(origLine.getReference())
                    .build();
            reversal.getLines().add(reversalLine);
        }

        recalculateTotals(reversal);
        JournalEntry savedReversal = journalEntryRepository.save(reversal);

        original.setReversedByEntryId(savedReversal.getId());
        original.setStatus(JournalEntryStatus.REVERSED);
        journalEntryRepository.save(original);

        return JournalEntryResponse.fromEntity(savedReversal);
    }

    @Override
    @Transactional
    public JournalEntryResponse postDirect(UUID distributorId, JournalEntryRequest request, User currentUser) {
        GlPeriod period = glPeriodService.getOrCreatePeriodForAutoPosting(distributorId, request.getEntryDate());
        validateLines(request.getLines());

        JournalEntry entry = buildEntry(distributorId, request, period, currentUser, JournalEntryStatus.POSTED);
        entry.setPostedAt(LocalDateTime.now());
        entry.setPostedBy(currentUser != null ? currentUser.getId() : null);
        populateLines(entry, request.getLines());

        return JournalEntryResponse.fromEntity(journalEntryRepository.save(entry));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private JournalEntry findById(UUID id) {
        return journalEntryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("JournalEntry", "id", id));
    }

    private void validateLines(List<JournalEntryLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("Journal entry must have at least one line");
        }
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (JournalEntryLineRequest line : lines) {
            BigDecimal dr = line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO;
            BigDecimal cr = line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO;
            totalDebit = totalDebit.add(dr);
            totalCredit = totalCredit.add(cr);
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new ValidationException(
                    "Journal entry is not balanced: total debits " + totalDebit + " != total credits " + totalCredit);
        }
    }

    private JournalEntry buildEntry(UUID distributorId, JournalEntryRequest request,
                                     GlPeriod period, User currentUser, JournalEntryStatus status) {
        String entryNumber = generateEntryNumber(distributorId, request.getEntryDate());
        JournalEntry entry = JournalEntry.builder()
                .distributorId(distributorId)
                .entryNumber(entryNumber)
                .periodId(period.getId())
                .period(period)
                .entryDate(request.getEntryDate())
                .description(request.getDescription())
                .reference(request.getReference())
                .sourceModule(request.getSourceModule() != null ? request.getSourceModule() : JournalSourceModule.MANUAL)
                .sourceDocumentId(request.getSourceDocumentId())
                .status(status)
                .createdBy(currentUser != null ? currentUser.getId() : null)
                .build();
        entry.setLines(new ArrayList<>());
        return entry;
    }

    private void populateLines(JournalEntry entry, List<JournalEntryLineRequest> lineRequests) {
        int lineNum = 1;
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (JournalEntryLineRequest lineReq : lineRequests) {
            GlAccount account = glAccountRepository.findById(lineReq.getAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("GlAccount", "id", lineReq.getAccountId()));

            CostCenter costCenter = null;
            if (lineReq.getCostCenterId() != null) {
                costCenter = costCenterRepository.findById(lineReq.getCostCenterId())
                        .orElseThrow(() -> new ResourceNotFoundException("CostCenter", "id", lineReq.getCostCenterId()));
            }

            BigDecimal dr = lineReq.getDebitAmount() != null ? lineReq.getDebitAmount() : BigDecimal.ZERO;
            BigDecimal cr = lineReq.getCreditAmount() != null ? lineReq.getCreditAmount() : BigDecimal.ZERO;

            JournalEntryLine line = JournalEntryLine.builder()
                    .journalEntry(entry)
                    .lineNumber(lineNum++)
                    .account(account)
                    .costCenter(costCenter)
                    .description(lineReq.getDescription())
                    .debitAmount(dr)
                    .creditAmount(cr)
                    .reference(lineReq.getReference())
                    .build();

            entry.getLines().add(line);
            totalDebit = totalDebit.add(dr);
            totalCredit = totalCredit.add(cr);
        }

        entry.setTotalDebit(totalDebit);
        entry.setTotalCredit(totalCredit);
    }

    private void recalculateTotals(JournalEntry entry) {
        BigDecimal dr = entry.getLines().stream().map(JournalEntryLine::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cr = entry.getLines().stream().map(JournalEntryLine::getCreditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        entry.setTotalDebit(dr);
        entry.setTotalCredit(cr);
    }

    private String generateEntryNumber(UUID distributorId, LocalDate date) {
        String prefix = "JE-" + date.format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";
        int nextSeq = journalEntryRepository.findMaxSequenceForPrefix(distributorId, prefix) + 1;
        return prefix + String.format("%04d", nextSeq);
    }
}
