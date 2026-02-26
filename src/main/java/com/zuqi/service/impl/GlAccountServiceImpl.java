package com.zuqi.service.impl;

import com.zuqi.api.dto.gl.GlAccountRequest;
import com.zuqi.api.dto.gl.GlAccountResponse;
import com.zuqi.domain.gl.GlAccount;
import com.zuqi.domain.gl.NormalBalance;
import com.zuqi.domain.gl.AccountType;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.GlAccountRepository;
import com.zuqi.service.GlAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GlAccountServiceImpl implements GlAccountService {

    private final GlAccountRepository glAccountRepository;

    @Override
    public List<GlAccountResponse> getAll(UUID distributorId) {
        return glAccountRepository.findByDistributorIdOrderByAccountCodeAsc(distributorId)
                .stream()
                .map(GlAccountResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public GlAccountResponse getById(UUID id) {
        return GlAccountResponse.fromEntity(findById(id));
    }

    @Override
    @Transactional
    public GlAccountResponse create(UUID distributorId, GlAccountRequest request, User currentUser) {
        if (glAccountRepository.existsByDistributorIdAndAccountCode(distributorId, request.getAccountCode())) {
            throw new ValidationException("Account code '" + request.getAccountCode() + "' already exists for this distributor");
        }

        int level = 1;
        if (request.getParentId() != null) {
            GlAccount parent = findById(request.getParentId());
            level = parent.getLevel() + 1;
        }

        GlAccount account = GlAccount.builder()
                .distributorId(distributorId)
                .accountCode(request.getAccountCode())
                .accountName(request.getAccountName())
                .accountType(request.getAccountType())
                .accountSubType(request.getAccountSubType())
                .normalBalance(deriveNormalBalance(request.getAccountType()))
                .parentId(request.getParentId())
                .level(level)
                .isPostingAccount(request.isPostingAccount())
                .isSystemAccount(false)
                .description(request.getDescription())
                .active(true)
                .build();

        return GlAccountResponse.fromEntity(glAccountRepository.save(account));
    }

    @Override
    @Transactional
    public GlAccountResponse update(UUID id, GlAccountRequest request, User currentUser) {
        GlAccount account = findById(id);

        if (account.isSystemAccount() && !account.getAccountCode().equals(request.getAccountCode())) {
            throw new ValidationException("Cannot change account code of a system account");
        }

        if (!account.getAccountCode().equals(request.getAccountCode()) &&
                glAccountRepository.existsByDistributorIdAndAccountCode(account.getDistributorId(), request.getAccountCode())) {
            throw new ValidationException("Account code '" + request.getAccountCode() + "' already exists");
        }

        account.setAccountCode(request.getAccountCode());
        account.setAccountName(request.getAccountName());
        account.setAccountType(request.getAccountType());
        account.setAccountSubType(request.getAccountSubType());
        account.setNormalBalance(deriveNormalBalance(request.getAccountType()));
        account.setParentId(request.getParentId());
        account.setPostingAccount(request.isPostingAccount());
        account.setDescription(request.getDescription());

        return GlAccountResponse.fromEntity(glAccountRepository.save(account));
    }

    @Override
    @Transactional
    public void deactivate(UUID id, User currentUser) {
        GlAccount account = findById(id);
        if (account.isSystemAccount()) {
            throw new ValidationException("Cannot deactivate a system account");
        }
        account.setActive(false);
        glAccountRepository.save(account);
    }

    private GlAccount findById(UUID id) {
        return glAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GlAccount", "id", id));
    }

    private NormalBalance deriveNormalBalance(AccountType type) {
        return switch (type) {
            case ASSET, EXPENSE -> NormalBalance.DEBIT;
            case LIABILITY, EQUITY, REVENUE -> NormalBalance.CREDIT;
        };
    }
}
