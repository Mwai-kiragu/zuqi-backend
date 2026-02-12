/**
 * Route Optimization Package
 *
 * <p>Delivery route optimization using Timefold solver and GraphHopper for Kenya roads.
 *
 * <p><b>Components:</b>
 * <ul>
 *   <li>RouteSolver - Timefold solver wrapper</li>
 *   <li>DistanceMatrixService - GraphHopper distance/time computation</li>
 *   <li>RouteOptimizationJob - Evening batch route planning</li>
 *   <li>domain/ - Timefold planning entities (Vehicle, DeliveryStop, RoutePlan)</li>
 * </ul>
 *
 * <p><b>Implementation Plan Reference:</b> Phase 5, Tasks 5.1-5.3
 * <p><b>Blueprint Reference:</b> plan.md Section 6.5 (Route Optimization Module)
 *
 * @since Phase 5
 */
package com.zuqi.ai.routing;
