package com.zuqi.service;

import com.zuqi.api.dto.ft.FtAmountRangeRequest;
import com.zuqi.api.dto.ft.FtAmountRangeResponse;
import com.zuqi.api.dto.ft.FundsTransferRequest;
import com.zuqi.api.dto.ft.FundsTransferResponse;
import com.zuqi.domain.ft.FundsTransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FundsTransferService {

    // Transfer CRUD + lifecycle
    Page<FundsTransferResponse> getAll(FundsTransferStatus status, LocalDate startDate, LocalDate endDate, Pageable pageable);
    FundsTransferResponse getById(UUID id);
    FundsTransferResponse create(UUID distributorId, FundsTransferRequest request);
    FundsTransferResponse update(UUID id, FundsTransferRequest request);
    FundsTransferResponse submit(UUID id);
    FundsTransferResponse approve(UUID id, String comment);
    FundsTransferResponse reject(UUID id, String reason);
    FundsTransferResponse cancel(UUID id);

    // Amount range configuration
    List<FtAmountRangeResponse> getAmountRanges(UUID distributorId);
    FtAmountRangeResponse createAmountRange(UUID distributorId, FtAmountRangeRequest request);
    FtAmountRangeResponse updateAmountRange(UUID rangeId, FtAmountRangeRequest request);
    void deleteAmountRange(UUID rangeId);
}
