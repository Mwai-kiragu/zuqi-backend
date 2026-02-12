/**
 * Demand Forecasting Package
 *
 * <p>ML-powered demand forecasting and order suggestions for sales reps.
 *
 * <p><b>Components:</b>
 * <ul>
 *   <li>DemandForecaster - Tribuo XGBoost regression model</li>
 *   <li>DemandFeatureBuilder - Builds demand-specific feature vectors</li>
 *   <li>DemandForecastJob - Spring Batch nightly forecast job</li>
 *   <li>OrderSuggestionService - Generates sales rep suggestions</li>
 * </ul>
 *
 * <p><b>Implementation Plan Reference:</b> Phase 3, Tasks 3.3-3.6
 * <p><b>Blueprint Reference:</b> plan.md Section 6.2 (Demand Forecasting Module)
 *
 * @since Phase 3
 */
package com.zuqi.ai.demand;
