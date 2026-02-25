package com.zuqi.api.dto.gl;

import com.zuqi.domain.gl.CostCenter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostCenterResponse {

    private UUID id;
    private UUID distributorId;
    private String code;
    private String name;
    private String description;
    private UUID parentId;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CostCenterResponse fromEntity(CostCenter c) {
        return CostCenterResponse.builder()
                .id(c.getId())
                .distributorId(c.getDistributorId())
                .code(c.getCode())
                .name(c.getName())
                .description(c.getDescription())
                .parentId(c.getParentId())
                .active(c.isActive())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
