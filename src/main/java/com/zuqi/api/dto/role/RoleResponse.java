package com.zuqi.api.dto.role;

import com.zuqi.domain.user.Permission;
import com.zuqi.domain.user.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {

    private Long id;
    private String name;
    private String description;
    private boolean systemRole;
    private Set<PermissionResponse> permissions;

    public static RoleResponse fromEntity(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .systemRole(role.isSystemRole())
                .permissions(role.getPermissions().stream()
                        .map(PermissionResponse::fromEntity)
                        .collect(Collectors.toSet()))
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionResponse {
        private Long id;
        private String name;
        private String description;
        private String module;

        public static PermissionResponse fromEntity(Permission permission) {
            return PermissionResponse.builder()
                    .id(permission.getId())
                    .name(permission.getName())
                    .description(permission.getDescription())
                    .module(permission.getModule())
                    .build();
        }
    }
}
