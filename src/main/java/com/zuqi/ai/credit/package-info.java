/**
 * Credit Risk Scoring Package
 *
 * <p>AI-powered credit risk evaluation for merchants using LLM-based scoring
 * that evolves to ML classification as data accumulates.
 *
 * <p><b>Components:</b>
 * <ul>
 *   <li>CreditScoringAiService - LangChain4j AI Service for LLM-based evaluation</li>
 *   <li>CreditClassifier - Tribuo XGBoost classifier (Phase 2+)</li>
 *   <li>CreditFeatureBuilder - Builds credit-specific feature vectors</li>
 *   <li>CreditExplainer - LLM-based explanation generator</li>
 *   <li>CreditLimitAdjuster - Dynamic limit adjustment logic</li>
 * </ul>
 *
 * <p><b>Implementation Plan Reference:</b> Phase 2, Tasks 2.4-2.7
 * <p><b>Blueprint Reference:</b> plan.md Section 6.1 (Credit Risk Module)
 *
 * @since Phase 2
 */
package com.zuqi.ai.credit;
