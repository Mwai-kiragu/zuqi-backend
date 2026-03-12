package com.zuqi.service;

import com.zuqi.api.dto.gl.JournalEntryRequest;
import com.zuqi.api.dto.gl.JournalEntryResponse;
import com.zuqi.domain.gl.JournalEntryStatus;
import com.zuqi.domain.gl.JournalSourceModule;
import com.zuqi.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface JournalEntryService {
    Page<JournalEntryResponse> getAll(UUID distributorId, UUID merchantId, JournalEntryStatus status,
                                       LocalDate fromDate, LocalDate toDate,
                                       JournalSourceModule sourceModule, Pageable pageable);
    JournalEntryResponse getById(UUID id);
    JournalEntryResponse create(UUID distributorId, JournalEntryRequest request, User currentUser);
    JournalEntryResponse update(UUID id, JournalEntryRequest request, User currentUser);
    JournalEntryResponse submit(UUID id, User currentUser);
    JournalEntryResponse approve(UUID id, User currentUser, String comments);
    JournalEntryResponse reject(UUID id, User currentUser, String reason);
    JournalEntryResponse reverse(UUID id, User currentUser);
    JournalEntryResponse postDirect(UUID distributorId, JournalEntryRequest request, User currentUser);
}
