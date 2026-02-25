package com.zuqi.service;

import com.zuqi.api.dto.gl.JournalEntryResponse;
import com.zuqi.domain.gl.JournalSourceModule;
import com.zuqi.domain.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface GlPostingService {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class PostingLine {
        private UUID accountId;
        private UUID costCenterId;
        private String description;
        private BigDecimal debitAmount;
        private BigDecimal creditAmount;
        private String reference;
    }

    JournalEntryResponse post(UUID distributorId,
                               JournalSourceModule sourceModule,
                               UUID sourceDocumentId,
                               LocalDate date,
                               String description,
                               String reference,
                               List<PostingLine> lines,
                               User currentUser);
}
