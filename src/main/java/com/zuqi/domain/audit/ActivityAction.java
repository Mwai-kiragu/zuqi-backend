package com.zuqi.domain.audit;

public enum ActivityAction {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    APPROVE,
    REJECT,
    CANCEL,
    ACTIVATE,
    DEACTIVATE,
    LOGIN,
    LOGOUT,
    LOGIN_FAILED,
    EXPORT,
    IMPORT,
    SEND,
    RECONCILE,
    STOCK_ADJUST,
    STOCK_TAKE,
    PASSWORD_RESET,
    PASSWORD_CHANGE,
    ENABLE_2FA,
    DISABLE_2FA
}
