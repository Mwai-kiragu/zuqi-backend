package com.zuqi.api.dto.gl;

import com.zuqi.domain.gl.AccountSubType;
import com.zuqi.domain.gl.AccountType;
import com.zuqi.domain.gl.SystemAccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlAccountRequest {

    @NotBlank(message = "Account code is required")
    @Size(max = 20)
    private String accountCode;

    @NotBlank(message = "Account name is required")
    @Size(max = 200)
    private String accountName;

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    @NotNull(message = "Account sub-type is required")
    private AccountSubType accountSubType;

    private UUID parentId;

    private boolean isPostingAccount = true;

    private String description;

    /** Optional: tag this account for automatic GL posting. */
    private SystemAccountType systemAccountType;
}
