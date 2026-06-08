package com.zuqi.service;

import com.zuqi.api.dto.returns.ApplyCreditNoteRequest;
import com.zuqi.api.dto.returns.CreditNoteResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CreditNoteService {

    Page<CreditNoteResponse> getAll(Pageable pageable);

    CreditNoteResponse getById(UUID id);

    CreditNoteResponse getBySalesReturn(UUID salesReturnId);

    /** Apply (part of) this credit note balance to an invoice. */
    CreditNoteResponse apply(UUID creditNoteId, ApplyCreditNoteRequest request);

    /** Mark as REFUNDED — cash refund was issued to the customer. */
    CreditNoteResponse markRefunded(UUID creditNoteId);
}
