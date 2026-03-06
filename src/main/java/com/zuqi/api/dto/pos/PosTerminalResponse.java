package com.zuqi.api.dto.pos;

import com.zuqi.domain.pos.PosTerminalStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PosTerminalResponse {

    private UUID id;
    private UUID branchId;
    private String branchName;
    private String name;
    private String code;
    private PosTerminalStatus status;
    private LocalDateTime createdAt;
}
