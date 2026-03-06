package com.zuqi.service;

import com.zuqi.api.dto.inventory.StockTransferRequest;
import com.zuqi.api.dto.inventory.StockTransferResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface StockTransferService {

    StockTransferResponse createTransfer(StockTransferRequest request, UUID requestedByUserId);

    Page<StockTransferResponse> getTransfers(String status, Pageable pageable);

    StockTransferResponse getTransferById(UUID transferId);

    StockTransferResponse approveTransfer(UUID transferId, UUID approvedByUserId);

    StockTransferResponse cancelTransfer(UUID transferId, String reason);

    StockTransferResponse receiveTransfer(UUID transferId, UUID receivedByUserId);
}
