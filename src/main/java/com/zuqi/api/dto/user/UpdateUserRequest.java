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
public class UpdateUserRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String phoneNumber;

    @NotBlank(message = "Role is required")
    private String role;

    private UUID distributorId;

    private UUID merchantId;

    private Boolean active;

    /** Optional: additional workflow tier role (INITIATOR / VERIFIER / AUTHORIZER). Empty string = remove tier role. */
    private String workflowTierRole;

    /** Optional: assign user to a UserGroup (drives module permissions + workflow tier). */
    private UUID userGroupId;
}
