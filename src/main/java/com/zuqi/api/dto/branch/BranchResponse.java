package com.zuqi.api.dto.branch;

import com.zuqi.domain.branch.BranchStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class BranchResponse {

    private UUID id;
    private UUID distributorId;
    private String distributorName;
    private String name;
    private String code;
    private String address;
    private String city;
    private String phone;
    private String email;
    private BranchStatus status;
    private boolean headquarters;
    private UUID managerId;
    private String managerName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
