package com.zuqi.service;

import com.zuqi.api.dto.gl.GlPeriodRequest;
import com.zuqi.api.dto.gl.GlPeriodResponse;
import com.zuqi.domain.gl.GlPeriod;
import com.zuqi.domain.user.User;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface GlPeriodService {
    List<GlPeriodResponse> getAll(UUID distributorId);
    GlPeriodResponse getById(UUID id);
    GlPeriodResponse getOrCreate(UUID distributorId, int year, int month, User currentUser);
    GlPeriodResponse create(UUID distributorId, GlPeriodRequest request, User currentUser);
    GlPeriodResponse close(UUID id, User currentUser);
    GlPeriodResponse lock(UUID id, User currentUser);
    GlPeriodResponse reopen(UUID id, User currentUser);
    GlPeriod getOpenPeriodForDate(UUID distributorId, LocalDate date);

    /**
     * Returns the accounting period for the given date, creating it (as OPEN) if it doesn't
     * exist yet. Intended for auto-posting only — never throws for a missing period.
     */
    GlPeriod getOrCreatePeriodForAutoPosting(UUID distributorId, LocalDate date);
}
