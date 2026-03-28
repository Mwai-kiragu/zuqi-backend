package com.zuqi.service.impl;

import com.zuqi.api.dto.crm.CustomerInteractionRequest;
import com.zuqi.api.dto.crm.CustomerInteractionResponse;
import com.zuqi.domain.crm.CustomerInteraction;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.CustomerInteractionRepository;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.service.CustomerInteractionService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerInteractionServiceImpl implements CustomerInteractionService {

    private final CustomerInteractionRepository interactionRepository;
    private final CustomerRepository customerRepository;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    public CustomerInteractionResponse create(CustomerInteractionRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getCustomerId()));

        User currentUser = securityUtils.getCurrentUser();

        CustomerInteraction interaction = CustomerInteraction.builder()
                .customer(customer)
                .distributorId(securityUtils.getCurrentUserDistributorId() != null
                        ? securityUtils.getCurrentUserDistributorId()
                        : (customer.getDistributor() != null ? customer.getDistributor().getId() : null))
                .interactionType(request.getInteractionType())
                .subject(request.getSubject())
                .notes(request.getNotes())
                .outcome(request.getOutcome())
                .followUpDate(request.getFollowUpDate())
                .followUpDone(request.getFollowUpDone() != null && request.getFollowUpDone())
                .createdBy(currentUser)
                .build();

        return CustomerInteractionResponse.from(interactionRepository.save(interaction));
    }

    @Override
    @Transactional
    public CustomerInteractionResponse update(UUID id, CustomerInteractionRequest request) {
        CustomerInteraction interaction = interactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerInteraction", "id", id));

        interaction.setInteractionType(request.getInteractionType());
        interaction.setSubject(request.getSubject());
        interaction.setNotes(request.getNotes());
        interaction.setOutcome(request.getOutcome());
        interaction.setFollowUpDate(request.getFollowUpDate());
        if (request.getFollowUpDone() != null) {
            interaction.setFollowUpDone(request.getFollowUpDone());
        }

        return CustomerInteractionResponse.from(interactionRepository.save(interaction));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!interactionRepository.existsById(id)) {
            throw new ResourceNotFoundException("CustomerInteraction", "id", id);
        }
        interactionRepository.deleteById(id);
    }

    @Override
    public CustomerInteractionResponse getById(UUID id) {
        return CustomerInteractionResponse.from(interactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerInteraction", "id", id)));
    }

    @Override
    public Page<CustomerInteractionResponse> getAll(Pageable pageable) {
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            return interactionRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable)
                    .map(CustomerInteractionResponse::from);
        }
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        if (distributorId != null) {
            return interactionRepository.findByDistributorIdOrderByCreatedAtDesc(distributorId, pageable)
                    .map(CustomerInteractionResponse::from);
        }
        return interactionRepository.findAll(pageable).map(CustomerInteractionResponse::from);
    }

    @Override
    public Page<CustomerInteractionResponse> getByCustomerId(UUID customerId, Pageable pageable) {
        return interactionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(CustomerInteractionResponse::from);
    }

    @Override
    public Page<CustomerInteractionResponse> getPendingFollowUps(Pageable pageable) {
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        if (distributorId == null) {
            throw new ValidationException("Distributor context required for follow-up queries");
        }
        return interactionRepository.findPendingFollowUpsByDistributorId(distributorId, pageable)
                .map(CustomerInteractionResponse::from);
    }
}
