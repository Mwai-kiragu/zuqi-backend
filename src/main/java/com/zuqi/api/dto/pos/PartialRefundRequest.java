package com.zuqi.api.dto.pos;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class PartialRefundRequest {

    /** Item-level refund: specify which items and how many units to refund. */
    private List<ItemRefund> items;

    /** Amount-level refund: refund a custom monetary amount without specifying items. */
    private BigDecimal customAmount;

    private String reason;

    @Data
    public static class ItemRefund {
        private UUID itemId;
        private BigDecimal quantity;
    }
}
