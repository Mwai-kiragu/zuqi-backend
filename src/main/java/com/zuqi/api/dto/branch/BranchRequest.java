package com.zuqi.api.dto.branch;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class BranchRequest {

    @NotBlank(message = "Branch name is required")
    private String name;

    private String code;

    private String address;

    private String city;

    private String phone;

    private String email;

    private boolean headquarters;

    private UUID managerId;

    /** Required when SUPER_ADMIN creates a branch — specifies which distributor it belongs to. */
    private UUID distributorId;
}
