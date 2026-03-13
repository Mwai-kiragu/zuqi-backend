package com.zuqi.api.dto.pos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class UpdateSaleItemsRequest {

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<SaleItemRequest> items;
}
