package com.zuqi.domain.gl;

/**
 * Tags a GL account as a "system account" so the auto-posting service can
 * look it up without relying on a hardcoded account code.
 *
 * Each distributor maps their own chart-of-accounts entries to these types.
 * Auto-posting is silently skipped if a required type has not been mapped.
 */
public enum SystemAccountType {
    /** Cash drawer / bank current account — DR on cash received, CR on cash paid out */
    CASH_AND_BANK,

    /** Trade debtors / receivables — DR on invoice issued, CR when payment received */
    ACCOUNTS_RECEIVABLE,

    /** Goods / merchandise stock — DR on purchase receipt, CR on COGS entry */
    INVENTORY,

    /** Trade creditors / payables — CR on purchase receipt, DR when supplier paid */
    ACCOUNTS_PAYABLE,

    /** Product/service sales revenue — CR on invoice/POS sale */
    SALES_REVENUE,

    /** Cost of goods sold — DR on POS sale completion (matched with CR Inventory) */
    COST_OF_GOODS_SOLD,

    /** Miscellaneous income not from core sales */
    OTHER_INCOME,

    /** Miscellaneous operating expenses */
    OTHER_EXPENSE
}
