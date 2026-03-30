package com.zuqi.service;

import com.zuqi.api.dto.crm.CustomerInteractionRequest;
import com.zuqi.api.dto.crm.CustomerInteractionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CustomerInteractionService {

    CustomerInteractionResponse create(CustomerInteractionRequest request);

    CustomerInteractionResponse update(UUID id, CustomerInteractionRequest request);

    void delete(UUID id);

    CustomerInteractionResponse getById(UUID id);

    Page<CustomerInteractionResponse> getAll(Pageable pageable);

    Page<CustomerInteractionResponse> getByCustomerId(UUID customerId, Pageable pageable);

    Page<CustomerInteractionResponse> getPendingFollowUps(Pageable pageable);
}
