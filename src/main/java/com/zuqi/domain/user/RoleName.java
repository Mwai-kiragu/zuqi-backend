package com.zuqi.domain.user;

/**
 * Enumeration of available roles in the system.
 */
public enum RoleName {
    /**
     * Super administrator with full system access across all distributors
     */
    SUPER_ADMIN,

    /**
     * System administrator with full access
     */
    ADMIN,

    /**
     * Distributor company administrator
     */
    DISTRIBUTOR_ADMIN,

    /**
     * Field sales representative
     */
    SALES_REP,

    /**
     * Warehouse manager responsible for inventory
     */
    WAREHOUSE_MANAGER,

    /**
     * Retail merchant/shop owner
     */
    MERCHANT,

    /**
     * Finance team member for payment reconciliation
     */
    FINANCE,

    /**
     * Delivery driver
     */
    DRIVER
}
