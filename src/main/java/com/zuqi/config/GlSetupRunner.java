package com.zuqi.config;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.service.GlAccountService;
import com.zuqi.service.GlPeriodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GlSetupRunner implements ApplicationRunner {

    private final DistributorRepository distributorRepository;
    private final GlAccountService glAccountService;
    private final GlPeriodService glPeriodService;

    @Override
    public void run(ApplicationArguments args) {
        List<Distributor> distributors = distributorRepository.findByActiveTrue();
        if (distributors.isEmpty()) return;

        int year = LocalDate.now().getYear();
        log.info("GL setup runner: checking {} active distributors for missing GL accounts/periods", distributors.size());

        for (Distributor distributor : distributors) {
            // Seed default GL accounts (idempotent — skips existing)
            try {
                glAccountService.seedDefaultAccounts(distributor.getId(), null);
            } catch (Exception e) {
                log.warn("GL setup: skipped accounts for distributor {} — {}", distributor.getId(), e.getMessage());
            }

            // Create all 12 periods for current year (idempotent — getOrCreate)
            for (int month = 1; month <= 12; month++) {
                try {
                    glPeriodService.getOrCreate(distributor.getId(), year, month, null);
                } catch (Exception e) {
                    log.warn("GL setup: skipped period {}/{} for distributor {} — {}", year, month, distributor.getId(), e.getMessage());
                }
            }
        }

        log.info("GL setup runner: completed for {} distributors", distributors.size());
    }
}
