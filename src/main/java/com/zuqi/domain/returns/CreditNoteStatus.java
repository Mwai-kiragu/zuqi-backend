package com.zuqi.domain.returns;

public enum CreditNoteStatus {
    OPEN,               // Full balance available
    PARTIALLY_APPLIED,  // Some balance used, remainder still available
    FULLY_APPLIED,      // Balance exhausted via invoice applications
    REFUNDED,           // Cash refund issued — balance consumed
    EXPIRED             // Passed expiry date without full use
}
