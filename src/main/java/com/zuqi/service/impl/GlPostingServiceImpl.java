package com.zuqi.service.impl;

import com.zuqi.api.dto.gl.JournalEntryLineRequest;
import com.zuqi.api.dto.gl.JournalEntryRequest;
import com.zuqi.api.dto.gl.JournalEntryResponse;
import com.zuqi.domain.gl.JournalSourceModule;
import com.zuqi.domain.user.User;
import com.zuqi.service.GlPostingService;
import com.zuqi.service.JournalEntryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GlPostingServiceImpl implements GlPostingService {

    private final JournalEntryService journalEntryService;

    @Override
    @Transactional
    public JournalEntryResponse post(UUID distributorId,
                                      JournalSourceModule sourceModule,
                                      UUID sourceDocumentId,
                                      LocalDate date,
                                      String description,
                                      String reference,
                                      List<PostingLine> lines,
                                      User currentUser) {

        List<JournalEntryLineRequest> lineRequests = lines.stream()
                .map(l -> JournalEntryLineRequest.builder()
                        .accountId(l.getAccountId())
                        .costCenterId(l.getCostCenterId())
                        .description(l.getDescription())
                        .debitAmount(l.getDebitAmount())
                        .creditAmount(l.getCreditAmount())
                        .reference(l.getReference())
                        .build())
                .collect(Collectors.toList());

        JournalEntryRequest request = JournalEntryRequest.builder()
                .entryDate(date)
                .description(description)
                .reference(reference)
                .lines(lineRequests)
                .build();

        return journalEntryService.postDirect(distributorId, request, currentUser);
    }
}
