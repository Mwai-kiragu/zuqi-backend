package com.zuqi.api.dto.procurement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoConfirmationDetailsResponse {

    /** Action this token performs: CONFIRM | DECLINE | PARTIAL */
    private String action;

    /** Token lifecycle: ACTIVE | USED | EXPIRED */
    private String tokenStatus;

    private String poNumber;
    private String supplierName;
    private String distributorName;
    private List<Map<String, Object>> items;
    private BigDecimal totalAmount;
    private LocalDate expectedDeliveryDate;
    private String notes;

    /** Populated when tokenStatus == USED */
    private String supplierResponse;
    private String supplierNotes;
}
