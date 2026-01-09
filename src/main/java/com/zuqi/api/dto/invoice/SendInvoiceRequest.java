package com.zuqi.api.dto.invoice;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendInvoiceRequest {

    @Email(message = "Invalid email format")
    private String email;
}
