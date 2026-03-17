package com.zuqi.ai.assistant;

import com.zuqi.ai.agent.tools.*;
import com.zuqi.ai.assistant.tools.CreditSummaryTool;
import com.zuqi.ai.assistant.tools.DemandForecastSummaryTool;
import com.zuqi.ai.agent.tools.InvoiceTool;
import com.zuqi.ai.agent.tools.ExpensesTool;
import com.zuqi.ai.agent.tools.ProcurementTool;
import com.zuqi.ai.agent.tools.FundsTransferTool;
import com.zuqi.ai.agent.tools.PosSalesTool;
import com.zuqi.ai.agent.tools.StockTransferTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Layer 1 of three-layer AI chat security.
 *
 * Maps each user role to the subset of LangChain4j {@code @Tool}-annotated beans
 * it may use.  When an {@code AssistantAgent} is built with only these tools,
 * the role literally cannot call any tool outside its list — no prompt injection
 * can bypass a tool that was never registered in the agent's toolbox.
 *
 * Role → permitted tool methods:
 *   DRIVER            → deliveryMetricsTool
 *   SALES_REP         → salesTrendTool, merchantMetricsTool, deliveryMetricsTool, demandForecastSummaryTool
 *   WAREHOUSE_MANAGER → inventoryHealthTool, anomalyAlertsTool, demandForecastSummaryTool
 *   FINANCE           → paymentPerformanceTool, creditSummaryTool, salesTrendTool
 *   MERCHANT_ADMIN /
 *   CUSTOMER / MERCHANT → salesTrendTool, paymentPerformanceTool
 *   default (DISTRIBUTOR_ADMIN / SUPER_ADMIN / UNKNOWN) → all 9 tools
 */
@Component
@RequiredArgsConstructor
public class RoleAwareToolProvider {

    private final SalesTrendTool            salesTrendTool;
    private final InventoryHealthTool       inventoryHealthTool;
    private final PaymentPerformanceTool    paymentPerformanceTool;
    private final RepPerformanceTool        repPerformanceTool;
    private final MerchantMetricsTool       merchantMetricsTool;
    private final AnomalyAlertsTool         anomalyAlertsTool;
    private final DeliveryMetricsTool       deliveryMetricsTool;
    private final CreditSummaryTool         creditSummaryTool;
    private final DemandForecastSummaryTool demandForecastSummaryTool;
    private final InvoiceTool               invoiceTool;
    private final ExpensesTool              expensesTool;
    private final ProcurementTool           procurementTool;
    private final FundsTransferTool         fundsTransferTool;
    private final PosSalesTool              posSalesTool;
    private final StockTransferTool         stockTransferTool;

    /** Returns the tool instances permitted for the given role. */
    public List<Object> getToolsForRole(String role) {
        if (role == null) return allTools();
        return switch (role.toUpperCase()) {
            case "DRIVER" ->
                    List.of(deliveryMetricsTool);

            case "SALES_REP" ->
                    List.of(salesTrendTool, merchantMetricsTool, invoiceTool,
                            deliveryMetricsTool, demandForecastSummaryTool);

            case "WAREHOUSE_MANAGER" ->
                    List.of(inventoryHealthTool, anomalyAlertsTool, demandForecastSummaryTool,
                            stockTransferTool, procurementTool, posSalesTool);

            case "FINANCE" ->
                    List.of(paymentPerformanceTool, creditSummaryTool, salesTrendTool,
                            invoiceTool, expensesTool, fundsTransferTool);

            case "MERCHANT_ADMIN" ->
                    List.of(salesTrendTool, inventoryHealthTool, paymentPerformanceTool,
                            anomalyAlertsTool, demandForecastSummaryTool, invoiceTool,
                            expensesTool, procurementTool, posSalesTool, stockTransferTool);

            case "CUSTOMER", "MERCHANT" ->
                    List.of(salesTrendTool, paymentPerformanceTool, invoiceTool);

            // DISTRIBUTOR_ADMIN, SUPER_ADMIN, or unknown → full access
            default -> allTools();
        };
    }

    private List<Object> allTools() {
        return List.of(salesTrendTool, inventoryHealthTool, paymentPerformanceTool,
                repPerformanceTool, merchantMetricsTool, anomalyAlertsTool,
                deliveryMetricsTool, creditSummaryTool, demandForecastSummaryTool,
                invoiceTool, expensesTool, procurementTool,
                fundsTransferTool, posSalesTool, stockTransferTool);
    }
}
