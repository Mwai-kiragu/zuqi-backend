package com.zuqi.api.dto.distributor;

import com.zuqi.domain.distributor.Distributor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistributorResponse {
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private String city;
    private boolean active;

    public static DistributorResponse fromEntity(Distributor distributor) {
        return DistributorResponse.builder()
                .id(distributor.getId())
                .name(distributor.getName())
                .email(distributor.getEmail())
                .phone(distributor.getPhone())
                .city(distributor.getCity())
                .active(distributor.isActive())
                .build();
    }
}
