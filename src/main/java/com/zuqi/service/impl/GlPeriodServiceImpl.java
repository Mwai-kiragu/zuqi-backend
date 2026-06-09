package com.zuqi.service.impl;

import com.zuqi.api.dto.gl.GlPeriodRequest;
import com.zuqi.api.dto.gl.GlPeriodResponse;
import com.zuqi.domain.gl.GlPeriod;
import com.zuqi.domain.gl.GlPeriodStatus;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.GlPeriodRepository;
import com.zuqi.service.GlPeriodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GlPeriodServiceImpl implements GlPeriodService {

    private final GlPeriodRepository glPeriodRepository;

    @Override
    public List<GlPeriodResponse> getAll(UUID distributorId) {
        return glPeriodRepository.findByDistributorIdOrderByPeriodYearDescPeriodMonthDesc(distributorId)
                .stream()
                .map(GlPeriodResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public GlPeriodResponse getById(UUID id) {
        return GlPeriodResponse.fromEntity(findById(id));
    }

    @Override
    @Transactional
    public GlPeriodResponse getOrCreate(UUID distributorId, int year, int month, User currentUser) {
        return glPeriodRepository.findByDistributorIdAndPeriodYearAndPeriodMonth(distributorId, year, month)
                .map(GlPeriodResponse::fromEntity)
                .orElseGet(() -> create(distributorId,
                        GlPeriodRequest.builder().periodYear(year).periodMonth(month).build(), currentUser));
    }

    @Override
    @Transactional
    public GlPeriodResponse create(UUID distributorId, GlPeriodRequest request, User currentUser) {
        if (glPeriodRepository.findByDistributorIdAndPeriodYearAndPeriodMonth(distributorId, request.getPeriodYear(), request.getPeriodMonth()).isPresent()) {
            throw new ValidationException("Period " + request.getPeriodYear() + "/" + request.getPeriodMonth() + " already exists");
        }

        LocalDate startDate = LocalDate.of(request.getPeriodYear(), request.getPeriodMonth(), 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        String periodName = Month.of(request.getPeriodMonth()).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + "-" + request.getPeriodYear();

        GlPeriodStatus initialStatus = startDate.isAfter(LocalDate.now())
                ? GlPeriodStatus.FUTURE : GlPeriodStatus.OPEN;

        GlPeriod period = GlPeriod.builder()
                .distributorId(distributorId)
                .periodName(periodName)
                .periodYear(request.getPeriodYear())
                .periodMonth(request.getPeriodMonth())
                .startDate(startDate)
                .endDate(endDate)
                .gracePeriodDays(request.getGracePeriodDays())
                .status(initialStatus)
                .build();

        return GlPeriodResponse.fromEntity(glPeriodRepository.save(period));
    }

    /**
     * CLOSE: human action after month-end checklist.
     * Only LOCKED periods can be closed — auto-lock must fire (or manual lock done) first.
     */
    @Override
    @Transactional
    public GlPeriodResponse close(UUID id, String closedNotes, User currentUser) {
        GlPeriod period = findById(id);
        if (period.getStatus() != GlPeriodStatus.LOCKED) {
            throw new ValidationException("Period must be LOCKED before it can be closed. " +
                "Wait for auto-lock after the grace period, or lock it manually first.");
        }
        period.setStatus(GlPeriodStatus.CLOSED);
        period.setClosedAt(LocalDateTime.now());
        period.setClosedBy(currentUser.getId());
        period.setClosedNotes(closedNotes);
        log.info("Period {} manually CLOSED by {}", period.getPeriodName(), currentUser.getEmail());
        return GlPeriodResponse.fromEntity(glPeriodRepository.save(period));
    }

    /**
     * LOCK: blocks all posting. Can be triggered manually from OPEN,
     * or automatically by the scheduler via autoLockExpiredPeriods().
     */
    @Override
    @Transactional
    public GlPeriodResponse lock(UUID id, User currentUser) {
        GlPeriod period = findById(id);
        if (period.getStatus() != GlPeriodStatus.OPEN) {
            throw new ValidationException("Only OPEN periods can be locked");
        }
        period.setStatus(GlPeriodStatus.LOCKED);
        period.setLockedAt(LocalDateTime.now());
        period.setLockedBy(currentUser != null ? currentUser.getId() : null);
        period.setAutoLocked(false);
        log.info("Period {} manually LOCKED by {}", period.getPeriodName(),
                currentUser != null ? currentUser.getEmail() : "system");
        return GlPeriodResponse.fromEntity(glPeriodRepository.save(period));
    }

    /**
     * REOPEN: only LOCKED (not yet CLOSED) periods can be reopened.
     * Once CLOSED the period is permanent.
     */
    @Override
    @Transactional
    public GlPeriodResponse reopen(UUID id, User currentUser) {
        GlPeriod period = findById(id);
        if (period.getStatus() == GlPeriodStatus.CLOSED) {
            throw new ValidationException("Closed periods cannot be reopened. The period is permanently finalised.");
        }
        if (period.getStatus() != GlPeriodStatus.LOCKED) {
            throw new ValidationException("Only LOCKED periods can be reopened");
        }
        period.setStatus(GlPeriodStatus.OPEN);
        period.setLockedAt(null);
        period.setLockedBy(null);
        period.setAutoLocked(false);
        log.info("Period {} REOPENED by {}", period.getPeriodName(), currentUser.getEmail());
        return GlPeriodResponse.fromEntity(glPeriodRepository.save(period));
    }

    /**
     * Scheduler: runs daily at 02:00 — locks every OPEN period whose
     * (endDate + gracePeriodDays) < today.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    @Override
    public int autoLockExpiredPeriods() {
        LocalDate today = LocalDate.now();
        List<GlPeriod> candidates = glPeriodRepository.findByStatus(GlPeriodStatus.OPEN);
        int locked = 0;
        for (GlPeriod p : candidates) {
            LocalDate autoLockDate = p.getEndDate().plusDays(p.getGracePeriodDays() + 1);
            if (!today.isBefore(autoLockDate)) {
                p.setStatus(GlPeriodStatus.LOCKED);
                p.setLockedAt(LocalDateTime.now());
                p.setAutoLocked(true);
                glPeriodRepository.save(p);
                locked++;
                log.info("Auto-locked period {} for distributor {}", p.getPeriodName(), p.getDistributorId());
            }
        }
        if (locked > 0) {
            log.info("Auto-lock sweep complete: {} period(s) locked", locked);
        }
        return locked;
    }

    /**
     * Scheduler: runs daily at 00:01 — opens every FUTURE period whose startDate <= today.
     */
    @Scheduled(cron = "0 1 0 * * *")
    @Transactional
    public void autoActivateFuturePeriods() {
        LocalDate today = LocalDate.now();
        List<GlPeriod> futures = glPeriodRepository.findByStatus(GlPeriodStatus.FUTURE);
        int activated = 0;
        for (GlPeriod p : futures) {
            if (!p.getStartDate().isAfter(today)) {
                p.setStatus(GlPeriodStatus.OPEN);
                glPeriodRepository.save(p);
                activated++;
                log.info("Auto-activated period {} for distributor {}", p.getPeriodName(), p.getDistributorId());
            }
        }
        if (activated > 0) {
            log.info("Auto-activate sweep complete: {} period(s) opened", activated);
        }
    }

    @Override
    public GlPeriod getOpenPeriodForDate(UUID distributorId, LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        GlPeriod period = glPeriodRepository.findByDistributorIdAndPeriodYearAndPeriodMonth(distributorId, year, month)
                .orElseThrow(() -> new ValidationException("No accounting period exists for " + date + ". Please create the period first."));

        if (period.getStatus() == GlPeriodStatus.FUTURE) {
            throw new ValidationException("Period " + period.getPeriodName() + " has not started yet (starts " + period.getStartDate() + "). Posting is not allowed before the period start date.");
        }
        if (period.getStatus() == GlPeriodStatus.CLOSED) {
            throw new ValidationException("Period " + period.getPeriodName() + " is CLOSED. Posting is not allowed.");
        }
        if (period.getStatus() == GlPeriodStatus.LOCKED) {
            throw new ValidationException("Period " + period.getPeriodName() + " is LOCKED. Reopen it before posting.");
        }
        return period;
    }

    @Override
    @Transactional
    public GlPeriod getOrCreatePeriodForAutoPosting(UUID distributorId, LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        GlPeriod period = glPeriodRepository
                .findByDistributorIdAndPeriodYearAndPeriodMonth(distributorId, year, month)
                .orElseGet(() -> {
                    LocalDate startDate = LocalDate.of(year, month, 1);
                    LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
                    String name = Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + "-" + year;
                    return glPeriodRepository.save(GlPeriod.builder()
                            .distributorId(distributorId)
                            .periodName(name)
                            .periodYear(year)
                            .periodMonth(month)
                            .startDate(startDate)
                            .endDate(endDate)
                            .status(GlPeriodStatus.OPEN)
                            .build());
                });
        if (period.getStatus() == GlPeriodStatus.FUTURE) {
            throw new ValidationException("Period " + period.getPeriodName() + " has not started yet. Auto-posting is not allowed before the period start date.");
        }
        if (period.getStatus() == GlPeriodStatus.CLOSED) {
            throw new ValidationException("Period " + period.getPeriodName() + " is CLOSED. Auto-posting is not allowed.");
        }
        if (period.getStatus() == GlPeriodStatus.LOCKED) {
            throw new ValidationException("Period " + period.getPeriodName() + " is LOCKED. Auto-posting is not allowed.");
        }
        return period;
    }

    private GlPeriod findById(UUID id) {
        return glPeriodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GlPeriod", "id", id));
    }
}
