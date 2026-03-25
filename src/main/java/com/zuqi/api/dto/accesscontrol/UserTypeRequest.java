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

    @Valid
    private List<UserTypePermissionDto> permissions;
}
