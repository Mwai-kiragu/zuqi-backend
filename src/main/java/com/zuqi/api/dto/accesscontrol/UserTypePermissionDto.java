package com.zuqi.api.dto.accesscontrol;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserTypePermissionDto {
    @NotBlank(message = "Module is required for each permission")
    private String module;
    private boolean canCreate;
    private boolean canRead;
    private boolean canUpdate;
    private boolean canDelete;
    private boolean canApprove;
}
