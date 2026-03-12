package com.zuqi.domain.mpesa;

public enum MpesaTransactionType {
    PAYBILL("CustomerPayBillOnline"),
    TILL("CustomerBuyGoodsOnline");

    private final String darajaValue;

    MpesaTransactionType(String darajaValue) {
        this.darajaValue = darajaValue;
    }

    public String getDarajaValue() {
        return darajaValue;
    }
}
