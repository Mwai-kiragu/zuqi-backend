package com.zuqi.api.dto.auth;

import com.zuqi.domain.billing.BillingPackageType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantRegisterRequest {

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 100)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String phoneNumber;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Brand name is required")
    @Size(min = 2, max = 255)
    private String brandName;

    private String registrationNumber;
    private String brandPhone;
    private String address;
    private String city;
    private String country;

    @NotBlank(message = "Distributor company name is required")
    @Size(min = 2, max = 200)
    private String companyName;

    /** Package to assign on registration. Defaults to FREE_TRIAL if null. */
    private BillingPackageType packageType;

    /** Required only when packageType == CUSTOM */
    private List<String> customModules;
}
