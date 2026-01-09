package com.zuqi.api.dto.credit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditScoreResponse {

    private UUID id;
    private UUID merchantId;
    private String merchantName;
    private BigDecimal score;
    private String scoreGrade;
    private Map<String, Object> factors;
    private String modelVersion;
    private LocalDateTime validUntil;
    private LocalDateTime createdAt;
}
