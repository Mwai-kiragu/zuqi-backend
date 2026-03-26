package com.zuqi.api.dto.accesscontrol;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserTypeResponse {
    private UUID id;
    private String name;
    private String description;
    private UUID distributorId;
    private String baseRole;
    private List<UserTypePermissionDto> permissions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
