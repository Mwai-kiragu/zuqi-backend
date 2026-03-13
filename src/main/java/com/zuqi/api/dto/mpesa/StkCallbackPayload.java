package com.zuqi.api.dto.mpesa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StkCallbackPayload(

        @JsonProperty("transactionReference") String transactionReference,
        @JsonProperty("orderId") String orderId,
        @JsonProperty("merchantRequestId") String merchantRequestId,
        @JsonProperty("checkoutRequestId") String checkoutRequestId,
        @JsonProperty("resultCode") String resultCode,
        @JsonProperty("resultDesc") String resultDesc,
        @JsonProperty("phoneNumber") String phoneNumber,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("transactionDate") String transactionDate,
        @JsonProperty("businessId") String businessId,
        @JsonProperty("transactionType") String transactionType,
        @JsonProperty("customerReference") String customerReference,
        @JsonProperty("organizationReference") String organizationReference,
        @JsonProperty("mpesaReceiptNumber") String mpesaReceiptNumber
) {}
