package com.zuqi.api.dto.distributor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistributorRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String registrationNumber;

    private String taxId;

    @Email(message = "Invalid email format")
    private String email;

    private String phone;

    private String address;

    private String city;

    private String country;
}
