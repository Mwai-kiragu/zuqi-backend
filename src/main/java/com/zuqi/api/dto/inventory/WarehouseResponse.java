package com.zuqi.api.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseResponse {

    private UUID id;
    private String code;
    private String name;
    private String address;
    private String city;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private UUID distributorId;
    private String distributorName;
    private UUID managerId;
    private String managerName;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String deactivationReason;
    private LocalDateTime deactivatedAt;
    private String deactivatedByName;
}
