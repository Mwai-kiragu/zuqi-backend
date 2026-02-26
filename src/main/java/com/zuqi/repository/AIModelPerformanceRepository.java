package com.zuqi.repository;

import com.zuqi.domain.ai.AIModelPerformance;
import com.zuqi.domain.ai.MetricName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@code ai_model_performance} table.
 *
 * <p>Stores one row per (model_name, model_version, evaluation_date, metric_name)
 * combination. Use {@link #saveMetrics} helpers in service layer to write all
 * metrics for an evaluation in one call.
 *
 * <p><b>Migration:</b> V27__create_ai_model_performance.sql
 */
@Repository
public interface AIModelPerformanceRepository extends JpaRepository<AIModelPerformance, UUID> {

    /**
     * All metrics recorded for a specific model version, ordered by evaluation
     * date descending (newest first).
     */
    List<AIModelPerformance> findByModelNameAndModelVersionOrderByEvaluationDateDesc(
            String modelName, Integer modelVersion);

    /**
     * All metrics for a model across all versions, ordered newest first.
     * Used by the performance history endpoint.
     */
    List<AIModelPerformance> findByModelNameOrderByEvaluationDateDesc(String modelName);

    /**
     * All metrics recorded on a specific evaluation date for a model version.
     * Returns all metric types (accuracy, mae, etc.) for that snapshot.
     */
    List<AIModelPerformance> findByModelNameAndModelVersionAndEvaluationDate(
            String modelName, Integer modelVersion, LocalDate evaluationDate);

    /**
     * Trend data for a single metric across all evaluations of a model.
     * Useful for drift and accuracy-over-time charts.
     */
    List<AIModelPerformance> findByModelNameAndMetricNameOrderByEvaluationDateDesc(
            String modelName, MetricName metricName);

    /**
     * Most recent evaluation date for a model version, across any metric.
     */
    @Query("SELECT MAX(p.evaluationDate) FROM AIModelPerformance p " +
           "WHERE p.modelName = :modelName AND p.modelVersion = :version")
    Optional<LocalDate> findLatestEvaluationDate(
            @Param("modelName") String modelName,
            @Param("version") Integer version);

    /**
     * Check whether a metric has already been recorded for a given
     * model/version/date combination (prevents duplicate inserts).
     */
    boolean existsByModelNameAndModelVersionAndEvaluationDateAndMetricName(
            String modelName, Integer modelVersion, LocalDate evaluationDate, MetricName metricName);

    /**
     * Distinct evaluation dates for a model, newest first.
     * Used to page through performance history snapshots.
     */
    @Query("SELECT DISTINCT p.evaluationDate FROM AIModelPerformance p " +
           "WHERE p.modelName = :modelName " +
           "ORDER BY p.evaluationDate DESC")
    List<LocalDate> findDistinctEvaluationDates(@Param("modelName") String modelName);

    /**
     * Delete all performance records older than the given cutoff date.
     * Used for data retention / cleanup jobs.
     */
    void deleteByEvaluationDateBefore(LocalDate cutoffDate);
}
