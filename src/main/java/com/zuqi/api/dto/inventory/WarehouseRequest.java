package com.zuqi.api.dto.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseRequest {

    @NotBlank(message = "Warehouse code is required")
    @Size(max = 50, message = "Code must not exceed 50 characters")
    private String code;

    @NotBlank(message = "Warehouse name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    private String address;
    private String city;
    private BigDecimal latitude;
    private BigDecimal longitude;

    @NotNull(message = "Distributor ID is required")
    private UUID distributorId;

    private UUID managerId;
    private boolean active = true;
}
