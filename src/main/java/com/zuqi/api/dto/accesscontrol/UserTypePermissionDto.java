package com.zuqi.api.dto.accesscontrol;

import lombok.Data;

@Data
public class UserTypePermissionDto {
    private String module;
    private boolean canCreate;
    private boolean canRead;
    private boolean canUpdate;
    private boolean canDelete;
    private boolean canApprove;
}
