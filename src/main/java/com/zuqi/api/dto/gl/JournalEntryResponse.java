package com.zuqi.api.dto.gl;

import com.zuqi.domain.gl.JournalEntry;
import com.zuqi.domain.gl.JournalEntryStatus;
import com.zuqi.domain.gl.JournalSourceModule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryResponse {

    private UUID id;
    private UUID distributorId;
    private String entryNumber;
    private UUID periodId;
    private String periodName;
    private LocalDate entryDate;
    private String description;
    private String reference;
    private JournalSourceModule sourceModule;
    private UUID sourceDocumentId;
    private JournalEntryStatus status;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private boolean isReversal;
    private UUID reversalOfEntryId;
    private UUID reversedByEntryId;
    private LocalDateTime postedAt;
    private UUID postedBy;
    private String rejectionReason;
    private UUID createdBy;
    private List<JournalEntryLineResponse> lines;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static JournalEntryResponse fromEntity(JournalEntry je) {
        return JournalEntryResponse.builder()
                .id(je.getId())
                .distributorId(je.getDistributorId())
                .entryNumber(je.getEntryNumber())
                .periodId(je.getPeriodId())
                .periodName(je.getPeriod() != null ? je.getPeriod().getPeriodName() : null)
                .entryDate(je.getEntryDate())
                .description(je.getDescription())
                .reference(je.getReference())
                .sourceModule(je.getSourceModule())
                .sourceDocumentId(je.getSourceDocumentId())
                .status(je.getStatus())
                .totalDebit(je.getTotalDebit())
                .totalCredit(je.getTotalCredit())
                .isReversal(je.isReversal())
                .reversalOfEntryId(je.getReversalOfEntryId())
                .reversedByEntryId(je.getReversedByEntryId())
                .postedAt(je.getPostedAt())
                .postedBy(je.getPostedBy())
                .rejectionReason(je.getRejectionReason())
                .createdBy(je.getCreatedBy())
                .lines(je.getLines() != null
                        ? je.getLines().stream().map(JournalEntryLineResponse::fromEntity).collect(Collectors.toList())
                        : null)
                .createdAt(je.getCreatedAt())
                .updatedAt(je.getUpdatedAt())
                .build();
    }
}
