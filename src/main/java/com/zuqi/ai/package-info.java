/**
 * AI Integration Module for Zuqi Platform
 *
 * <p>This package contains all AI-powered capabilities for the Zuqi platform including:
 * <ul>
 *   <li>Credit risk scoring (LLM → ML evolution)</li>
 *   <li>Demand forecasting and order suggestions</li>
 *   <li>Anomaly detection (shrinkage, payments, data quality)</li>
 *   <li>Predictive alerts (stockouts, rep performance)</li>
 *   <li>Route optimization</li>
 *   <li>AI agent for operational recommendations</li>
 *   <li>Compliance reporting</li>
 * </ul>
 *
 * <p>Architecture follows the single-module approach with clean package separation.
 * All AI code is isolated under com.zuqi.ai with direct access to existing repositories
 * and services via standard Spring dependency injection.
 *
 * <p>Reference: See plan.md and implementation_plan.md for complete architecture and
 * implementation guidelines.
 *
 * @see <a href="../../../../../../../../../plan.md">AI System Architecture Blueprint</a>
 * @see <a href="../../../../../../../../../implementation_plan.md">Phased Implementation Plan</a>
 */
package com.zuqi.ai;
