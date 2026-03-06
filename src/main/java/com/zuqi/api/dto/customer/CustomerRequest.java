package com.zuqi.api.dto.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRequest {

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

    @Size(max = 100)
    private String county;

    @Size(max = 100)
    private String subCounty;

    @Pattern(regexp = "^[AP]\\d{9}[A-Z]$", message = "Invalid KRA PIN format (e.g. A123456789Z)")
    private String kraPin;

    private List<Map<String, Object>> contactPersons;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private Long categoryId;

    private UUID distributorId;

    private UUID assignedSalesRepId;

    private BigDecimal creditLimit;

    private Integer paymentTermsDays;
}
