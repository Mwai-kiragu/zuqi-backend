package com.zuqi.domain.procurement;

public enum GrnStatus {
    DRAFT,       // Created, items can still be edited
    CONFIRMED,   // Stock has been received and stock quantities updated
    REJECTED     // Delivery rejected (returned to supplier)
}
