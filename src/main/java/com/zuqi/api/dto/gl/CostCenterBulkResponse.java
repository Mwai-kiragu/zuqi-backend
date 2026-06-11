package com.zuqi.api.dto.gl;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CostCenterBulkResponse {

    private int created;
    private int skipped;
    private int failed;
    private List<RowError> errors;

    @Data
    @Builder
    public static class RowError {
        private int row;
        private String code;
        private String reason;
    }
}
