package com.zuqi.api.dto.pos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CloseShiftRequest {

    private BigDecimal closingFloat;

    private String notes;
}
