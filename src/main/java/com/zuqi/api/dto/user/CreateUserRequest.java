package com.zuqi.api.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String phoneNumber;

    private String password;

    @NotBlank(message = "Role is required")
    private String role;

    private UUID distributorId;

    private UUID merchantId;

    /** Optional: assign user to this branch. Defaults to the distributor's HQ branch if not provided. */
    private UUID branchId;

    /** Optional: role within the branch. Defaults to the system role if not provided. */
    private String branchRole;
}
