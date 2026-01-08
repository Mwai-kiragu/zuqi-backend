package com.zuqi.api.dto.role;

import com.zuqi.domain.user.Permission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionFullResponse {

    private Long id;
    private String name;
    private String description;
    private String module;

    public static PermissionFullResponse fromEntity(Permission permission) {
        return PermissionFullResponse.builder()
                .id(permission.getId())
                .name(permission.getName())
                .description(permission.getDescription())
                .module(permission.getModule())
                .build();
    }
}
