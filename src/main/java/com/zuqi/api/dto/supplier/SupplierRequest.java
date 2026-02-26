package com.zuqi.api.dto.supplier;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class SupplierRequest {

    @NotBlank(message = "Supplier name is required")
    @Size(max = 255)
    private String name;

    @Pattern(regexp = "^[AP]\\d{9}[A-Z]$", message = "Invalid KRA PIN format (e.g. A123456789Z)")
    private String kraPin;

    @Size(max = 50)
    private String registrationNumber;

    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Size(max = 20)
    private String phone;

    private String address;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String county;

    @Size(max = 100)
    private String subCounty;

    @Size(max = 100)
    private String bankName;

    @Size(max = 100)
    private String bankBranch;

    @Size(max = 50)
    private String bankAccountNumber;

    @Size(max = 100)
    private String bankAccountName;

    @Size(max = 20)
    private String swiftCode;

    private Integer paymentTermsDays;

    private BigDecimal creditLimit;

    private List<Map<String, Object>> contactPersons;

    private Long categoryId;

    private UUID distributorId;
}
