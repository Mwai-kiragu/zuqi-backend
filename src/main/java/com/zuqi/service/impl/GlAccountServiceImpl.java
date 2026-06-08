package com.zuqi.service.impl;

import com.zuqi.api.dto.gl.GlAccountRequest;
import com.zuqi.api.dto.gl.GlAccountResponse;
import com.zuqi.domain.gl.AccountSubType;
import com.zuqi.domain.gl.GlAccount;
import com.zuqi.domain.gl.NormalBalance;
import com.zuqi.domain.gl.AccountType;
import com.zuqi.domain.gl.SystemAccountType;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.GlAccountRepository;
import com.zuqi.repository.JournalEntryLineRepository;
import com.zuqi.service.GlAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GlAccountServiceImpl implements GlAccountService {

    private final GlAccountRepository glAccountRepository;
    private final JournalEntryLineRepository journalEntryLineRepository;

    @Override
    public List<GlAccountResponse> getAll(UUID distributorId, UUID merchantId) {
        // A concrete distributorId always takes precedence — use merchant-wide query only
        // when no specific distributor is known (MERCHANT_ADMIN viewing all distributors).
        if (distributorId != null) {
            return glAccountRepository.findByDistributorIdOrderByAccountCodeAsc(distributorId)
                    .stream().map(GlAccountResponse::fromEntity).collect(Collectors.toList());
        }
        if (merchantId != null) {
            return glAccountRepository.findByDistributorMerchantIdOrderByAccountCodeAsc(merchantId)
                    .stream().map(GlAccountResponse::fromEntity).collect(Collectors.toList());
        }
        return glAccountRepository.findAll(
                org.springframework.data.domain.Sort.by("accountCode"))
                .stream().map(GlAccountResponse::fromEntity).collect(Collectors.toList());
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
                .systemAccountType(request.getSystemAccountType())
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
        account.setSystemAccountType(request.getSystemAccountType());
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
        if (journalEntryLineRepository.existsByAccountId(id)) {
            throw new ValidationException(
                "Cannot deactivate account '" + account.getAccountCode() + " – " + account.getAccountName() +
                "': it has existing journal entry lines. Deactivating it would break historical records.");
        }
        if (glAccountRepository.existsByParentId(id)) {
            throw new ValidationException(
                "Cannot deactivate account '" + account.getAccountCode() + " – " + account.getAccountName() +
                "': it has child accounts. Deactivate or reassign the child accounts first.");
        }
        account.setActive(false);
        glAccountRepository.save(account);
    }

    private GlAccount findById(UUID id) {
        return glAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GlAccount", "id", id));
    }

    @Override
    public boolean hasAccountsSetUp(UUID distributorId) {
        return distributorId != null && glAccountRepository.existsByDistributorId(distributorId);
    }

    @Override
    @Transactional
    public List<GlAccountResponse> seedDefaultAccounts(UUID distributorId, User currentUser) {
        record Seed(String code, String name, AccountType type, AccountSubType subType, SystemAccountType sysType) {}

        List<Seed> seeds = List.of(
            // ── ASSETS ──────────────────────────────────────────────────────
            new Seed("1000", "Cash",                   AccountType.ASSET,   AccountSubType.CURRENT_ASSET,     SystemAccountType.CASH_AND_BANK),
            new Seed("1010", "Petty Cash",             AccountType.ASSET,   AccountSubType.CURRENT_ASSET,     null),
            new Seed("1100", "Accounts Receivable",    AccountType.ASSET,   AccountSubType.CURRENT_ASSET,     SystemAccountType.ACCOUNTS_RECEIVABLE),
            new Seed("1200", "Inventory",              AccountType.ASSET,   AccountSubType.CURRENT_ASSET,     SystemAccountType.INVENTORY),
            new Seed("1300", "Prepaid Expenses",       AccountType.ASSET,   AccountSubType.CURRENT_ASSET,     null),
            new Seed("1500", "Property & Equipment",   AccountType.ASSET,   AccountSubType.FIXED_ASSET,       null),
            new Seed("1600", "Accumulated Depreciation",AccountType.ASSET,  AccountSubType.FIXED_ASSET,       null),
            // ── LIABILITIES ─────────────────────────────────────────────────
            new Seed("2000", "Accounts Payable",       AccountType.LIABILITY,AccountSubType.CURRENT_LIABILITY, SystemAccountType.ACCOUNTS_PAYABLE),
            new Seed("2100", "Accrued Liabilities",    AccountType.LIABILITY,AccountSubType.CURRENT_LIABILITY, null),
            new Seed("2200", "Tax Payable",            AccountType.LIABILITY,AccountSubType.CURRENT_LIABILITY, null),
            new Seed("2500", "Long-Term Debt",         AccountType.LIABILITY,AccountSubType.LONG_TERM_LIABILITY,null),
            // ── EQUITY ──────────────────────────────────────────────────────
            new Seed("3000", "Owner's Equity",         AccountType.EQUITY,  AccountSubType.RETAINED_EARNINGS, null),
            new Seed("3100", "Retained Earnings",      AccountType.EQUITY,  AccountSubType.RETAINED_EARNINGS, null),
            // ── REVENUE ─────────────────────────────────────────────────────
            new Seed("4000", "Sales Revenue",          AccountType.REVENUE, AccountSubType.OPERATING_REVENUE,  SystemAccountType.SALES_REVENUE),
            new Seed("4100", "Other Income",           AccountType.REVENUE, AccountSubType.OTHER_REVENUE,      SystemAccountType.OTHER_INCOME),
            // ── EXPENSES ────────────────────────────────────────────────────
            new Seed("5000", "Cost of Goods Sold",     AccountType.EXPENSE, AccountSubType.COGS,               SystemAccountType.COST_OF_GOODS_SOLD),
            new Seed("6000", "Salaries & Wages",       AccountType.EXPENSE, AccountSubType.OPERATING_EXPENSE,  null),
            new Seed("6100", "Rent Expense",           AccountType.EXPENSE, AccountSubType.OPERATING_EXPENSE,  null),
            new Seed("6200", "Utilities",              AccountType.EXPENSE, AccountSubType.OPERATING_EXPENSE,  null),
            new Seed("6300", "Depreciation Expense",   AccountType.EXPENSE, AccountSubType.OPERATING_EXPENSE,  null),
            new Seed("6900", "Other Expenses",         AccountType.EXPENSE, AccountSubType.OTHER_EXPENSE,      SystemAccountType.OTHER_EXPENSE)
        );

        List<GlAccountResponse> created = new ArrayList<>();
        for (Seed s : seeds) {
            GlAccount existing = glAccountRepository
                    .findByDistributorIdAndAccountCode(distributorId, s.code())
                    .orElse(null);

            if (existing != null) {
                // Patch: if system type is missing, fill it in so auto-posting can find it
                if (existing.getSystemAccountType() == null && s.sysType() != null) {
                    existing.setSystemAccountType(s.sysType());
                    existing.setSystemAccount(true);
                    glAccountRepository.save(existing);
                    log.info("Patched systemAccountType={} on GL account {} for distributor {}", s.sysType(), s.code(), distributorId);
                }
                continue;
            }

            GlAccount account = GlAccount.builder()
                    .distributorId(distributorId)
                    .accountCode(s.code())
                    .accountName(s.name())
                    .accountType(s.type())
                    .accountSubType(s.subType())
                    .normalBalance(deriveNormalBalance(s.type()))
                    .isPostingAccount(true)
                    .isSystemAccount(s.sysType() != null)
                    .systemAccountType(s.sysType())
                    .active(true)
                    .build();
            created.add(GlAccountResponse.fromEntity(glAccountRepository.save(account)));
        }
        log.info("Seeded {} GL accounts for distributor {}", created.size(), distributorId);
        return created;
    }

    private NormalBalance deriveNormalBalance(AccountType type) {
        return switch (type) {
            case ASSET, EXPENSE -> NormalBalance.DEBIT;
            case LIABILITY, EQUITY, REVENUE -> NormalBalance.CREDIT;
        };
    }
}
