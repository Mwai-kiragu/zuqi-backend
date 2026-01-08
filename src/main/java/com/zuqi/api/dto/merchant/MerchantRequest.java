package com.zuqi.api.dto.merchant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for creating/updating a merchant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantRequest {

    @NotBlank(message = "Business name is required")
    @Size(min = 2, max = 255, message = "Business name must be between 2 and 255 characters")
    private String businessName;

    @Size(max = 255, message = "Owner name must not exceed 255 characters")
    private String ownerName;

    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phone;

    private String address;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private Long categoryId;

    private UUID distributorId;

    private UUID assignedSalesRepId;

    private BigDecimal creditLimit;

    private Integer paymentTermsDays;
}
