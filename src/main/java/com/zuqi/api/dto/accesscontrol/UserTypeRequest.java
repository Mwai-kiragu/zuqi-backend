package com.zuqi.api.dto.accesscontrol;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UserTypeRequest {

    @NotBlank
    private String name;

    private String description;

    /** System role this type maps to (e.g. SALES_REP, FINANCE). Drives Casbin authorization. */
    private String baseRole;

    @Valid
    private List<UserTypePermissionDto> permissions;
}
