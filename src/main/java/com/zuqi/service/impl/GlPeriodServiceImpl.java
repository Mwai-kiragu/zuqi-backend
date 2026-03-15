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
                .orElseGet(() -> create(distributorId, new GlPeriodRequest(year, month), currentUser));
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

        GlPeriod period = GlPeriod.builder()
                .distributorId(distributorId)
                .periodName(periodName)
                .periodYear(request.getPeriodYear())
                .periodMonth(request.getPeriodMonth())
                .startDate(startDate)
                .endDate(endDate)
                .status(GlPeriodStatus.OPEN)
                .build();

        return GlPeriodResponse.fromEntity(glPeriodRepository.save(period));
    }

    @Override
    @Transactional
    public GlPeriodResponse close(UUID id, User currentUser) {
        GlPeriod period = findById(id);
        if (period.getStatus() != GlPeriodStatus.OPEN) {
            throw new ValidationException("Only OPEN periods can be closed");
        }
        period.setStatus(GlPeriodStatus.CLOSED);
        period.setClosedAt(LocalDateTime.now());
        period.setClosedBy(currentUser.getId());
        return GlPeriodResponse.fromEntity(glPeriodRepository.save(period));
    }

    @Override
    @Transactional
    public GlPeriodResponse lock(UUID id, User currentUser) {
        GlPeriod period = findById(id);
        if (period.getStatus() != GlPeriodStatus.CLOSED) {
            throw new ValidationException("Only CLOSED periods can be locked");
        }
        period.setStatus(GlPeriodStatus.LOCKED);
        period.setLockedAt(LocalDateTime.now());
        period.setLockedBy(currentUser.getId());
        return GlPeriodResponse.fromEntity(glPeriodRepository.save(period));
    }

    @Override
    @Transactional
    public GlPeriodResponse reopen(UUID id, User currentUser) {
        GlPeriod period = findById(id);
        if (period.getStatus() == GlPeriodStatus.LOCKED) {
            throw new ValidationException("Locked periods cannot be reopened");
        }
        if (period.getStatus() != GlPeriodStatus.CLOSED) {
            throw new ValidationException("Only CLOSED periods can be reopened");
        }
        period.setStatus(GlPeriodStatus.OPEN);
        period.setClosedAt(null);
        period.setClosedBy(null);
        return GlPeriodResponse.fromEntity(glPeriodRepository.save(period));
    }

    @Override
    public GlPeriod getOpenPeriodForDate(UUID distributorId, LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        GlPeriod period = glPeriodRepository.findByDistributorIdAndPeriodYearAndPeriodMonth(distributorId, year, month)
                .orElseThrow(() -> new ValidationException("No accounting period exists for " + date + ". Please create the period first."));

        if (period.getStatus() == GlPeriodStatus.LOCKED) {
            throw new ValidationException("Period " + period.getPeriodName() + " is LOCKED. Posting is not allowed.");
        }
        if (period.getStatus() == GlPeriodStatus.CLOSED) {
            throw new ValidationException("Period " + period.getPeriodName() + " is CLOSED. Reopen it before posting.");
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
        if (period.getStatus() == GlPeriodStatus.LOCKED) {
            throw new ValidationException("Period " + period.getPeriodName() + " is LOCKED. Auto-posting is not allowed.");
        }
        if (period.getStatus() == GlPeriodStatus.CLOSED) {
            throw new ValidationException("Period " + period.getPeriodName() + " is CLOSED. Reopen it before auto-posting.");
        }
        return period;
    }

    private GlPeriod findById(UUID id) {
        return glPeriodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GlPeriod", "id", id));
    }
}
