package com.zuqi.ai.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Static how-to guide tool for the AI assistant.
 *
 * Answers "how do I...?" questions with step-by-step instructions
 * based on Zuqi's actual frontend UI navigation and button labels.
 * No DB access — pure static lookup, available to every role.
 */
@Component
@Slf4j
public class HelpTool {

    private static final Map<String, String> GUIDES = Map.ofEntries(

        // ── Orders ──────────────────────────────────────────────
        Map.entry("create order",
            """
            {"topic": "Create an Order",
             "steps": [
               "Open the sidebar and go to Sales → Orders",
               "Click the 'Create Order' button in the top-right corner",
               "Select a customer from the search dropdown — the customer's credit grade and available credit limit are shown",
               "Browse or search for products; click 'Add' on each product you want",
               "Set the quantity for each line item in the order cart",
               "Review the order total and verify it is within the customer's available credit limit",
               "Click 'Submit Order' to place the order — status starts as PENDING",
               "The order moves through: PENDING → CONFIRMED → PROCESSING → READY FOR DELIVERY → DELIVERED"
             ],
             "tip": "If a customer has insufficient credit, you will see a warning. Contact Finance to adjust their limit first."}"""),

        // ── Invoices ─────────────────────────────────────────────
        Map.entry("create invoice",
            """
            {"topic": "Invoices",
             "steps": [
               "Invoices in Zuqi are generated automatically from orders and POS sales — there is no manual 'Create Invoice' button",
               "To find invoices, open the sidebar and go to Sales → Invoices",
               "Search or filter by status: Draft, Unpaid, Sent, Viewed, Paid, Partially Paid, Overdue, Cancelled",
               "To send an invoice to a customer by email, click the 'Send Invoice' icon on the invoice row",
               "Confirm the recipient email in the dialog and click 'Send Invoice'",
               "To cancel an invoice, click the 'Cancel Invoice' icon and confirm"
             ],
             "tip": "An invoice is created automatically when an order is confirmed. POS sales also generate invoices immediately after payment."}"""),

        Map.entry("send invoice",
            """
            {"topic": "Send an Invoice",
             "steps": [
               "Go to Sales → Invoices in the sidebar",
               "Find the invoice you want to send (use the search bar or filter by status UNPAID/DRAFT)",
               "Click the 'Send Invoice' icon button on the invoice row",
               "Confirm the recipient email address in the dialog",
               "Click 'Send Invoice' — invoice status changes to SENT",
               "The customer receives the invoice by email"
             ]}"""),

        // ── Customers ─────────────────────────────────────────────
        Map.entry("add customer",
            """
            {"topic": "Add a Customer",
             "steps": [
               "Open the sidebar and go to Sales → Customers",
               "Click the 'Add Customer' button",
               "Fill in the customer details: business name, contact person, phone number, email, and location",
               "Select the customer category (e.g. retailer, wholesaler)",
               "Optionally set an initial credit limit — limits above KES 50,000 require Finance approval",
               "Click 'Save' to create the customer",
               "The customer's AI credit score and grade (A–F) will appear once they have transaction history"
             ],
             "tip": "The credit grade is calculated automatically based on credit utilization. New customers start without a grade until they have transaction history."}"""),

        Map.entry("view customers",
            """
            {"topic": "View Customers",
             "steps": [
               "Open the sidebar and go to Sales → Customers",
               "The table shows: customer name, contact info, location, credit limit, balance, AI score, grade (A–F), and status",
               "Filter by status (Active / Inactive) using the dropdown",
               "Search by name using the search bar",
               "Click a customer row to view their full profile including order history and credit details"
             ]}"""),

        // ── Inventory / Stock ──────────────────────────────────────
        Map.entry("check stock",
            """
            {"topic": "Check Stock Levels",
             "steps": [
               "Open the sidebar and go to Inventory → Stock (for DISTRIBUTOR_ADMIN/MERCHANT_ADMIN)",
               "Or, from the sidebar click Inventory → the Inventory hub page shows four cards",
               "Click the 'Active Stock' card to go to the full stock view",
               "Select a warehouse from the dropdown to filter by location, or view all warehouses",
               "The table shows: product name, SKU, current quantity, reorder level, AI stockout risk, and days remaining",
               "Red rows indicate critical stockout risk (less than 3 days of stock)",
               "Click 'Reorder' on any low-stock row to start a purchase requisition for that product"
             ],
             "tip": "The AI Stockout Risk column uses the XGBoost predictor. A SYNTHETIC badge means predictions are advisory — the model is still in its training phase."}"""),

        Map.entry("add stock",
            """
            {"topic": "Add / Receive Stock",
             "steps": [
               "Open the sidebar and go to Inventory → the hub page",
               "Click the 'Add Stock' card",
               "Select the warehouse you are adding stock to",
               "Search for the product and enter the quantity received",
               "Enter the unit cost and the supplier reference or delivery note number",
               "Click 'Save' — stock levels are updated immediately and a stock movement record is created"
             ]}"""),

        // ── Stock Transfers ────────────────────────────────────────
        Map.entry("stock transfer",
            """
            {"topic": "Create a Stock Transfer",
             "steps": [
               "Open the sidebar and go to Inventory → then click the Inventory hub",
               "Click the 'Stock Transfers' card to open the transfers list",
               "Click 'Create Transfer' button",
               "In the dialog, select the Source Warehouse and Destination Warehouse",
               "Click 'Add Item' to add products to the transfer — search for product, enter quantity",
               "Add optional notes for the transfer",
               "Click 'Create Transfer' — status starts as PENDING",
               "A Warehouse Manager or Admin must approve the transfer",
               "Once approved, mark it as IN_TRANSIT when goods are dispatched",
               "When goods arrive at the destination, click 'Mark Received'"
             ],
             "tip": "You can filter transfers by status: PENDING, APPROVED, IN_TRANSIT, RECEIVED, CANCELLED."}"""),

        // ── Procurement ────────────────────────────────────────────
        Map.entry("create requisition",
            """
            {"topic": "Create a Purchase Requisition",
             "steps": [
               "Open the sidebar and go to Procurement → Requisitions",
               "Click 'New Requisition' button",
               "Select the supplier and the destination warehouse",
               "Add the products you need and their quantities",
               "Add a description or justification if required",
               "Click 'Save' — the requisition is created with DRAFT status",
               "Click 'Submit for Approval' on the requisition row to send it for review",
               "Once approved, use 'Convert to PO' to generate a Purchase Order"
             ]}"""),

        Map.entry("create purchase order",
            """
            {"topic": "Create a Purchase Order",
             "steps": [
               "Option 1 — Convert from an approved requisition: go to Procurement → Requisitions, find an APPROVED requisition, click 'Convert to PO'",
               "Option 2 — Create directly: go to Procurement → Purchase Orders, click 'New PO'",
               "Select the supplier, confirm the products and quantities",
               "Set the expected delivery date",
               "Click 'Send to Supplier' — status changes to SENT and the supplier is notified",
               "When goods arrive, open the PO and click 'Receive' to update stock levels",
               "Partial deliveries are recorded as PARTIALLY_RECEIVED until fully received"
             ]}"""),

        // ── Finance / Payments ─────────────────────────────────────
        Map.entry("record payment",
            """
            {"topic": "Record a Payment",
             "steps": [
               "Open the sidebar and go to Finance → Payments",
               "Click 'Record Payment' button",
               "Select the customer (merchant) making the payment",
               "Select the invoice being paid from the dropdown",
               "Enter the amount received",
               "Select the payment method: M-Pesa, Bank Transfer, Cash, or KCB",
               "Enter the payment reference number (M-Pesa transaction ID, bank reference, etc.)",
               "Click 'Save' — the payment is matched to the invoice automatically",
               "If the amount is less than the invoice total, it records as a partial payment (PARTIALLY_PAID)"
             ],
             "tip": "The AI payment anomaly detector monitors all payments. Unusual amounts or timing may trigger an alert in AI Intelligence → Anomaly Alerts."}"""),

        // ── Expenses ───────────────────────────────────────────────
        Map.entry("record expense",
            """
            {"topic": "Record an Expense",
             "steps": [
               "Open the sidebar and go to Finance → Expenses",
               "Click 'Add Expense' button",
               "Select the expense category (Rent, Utilities, Salaries, Marketing, Travel, Office Supplies, Maintenance, Insurance, Other)",
               "Enter the expense amount in KES, the date, and a description/title",
               "Attach a receipt or supporting document if available",
               "Click 'Save' — expense is created with DRAFT status",
               "Click 'Submit' on the expense row to send it for Finance approval",
               "Once approved, a Finance officer can mark it as PAID"
             ]}"""),

        // ── Funds Transfers ────────────────────────────────────────
        Map.entry("funds transfer",
            """
            {"topic": "Create a Funds Transfer",
             "steps": [
               "Open the sidebar and go to Finance → Funds Transfers",
               "Click 'New Transfer' button",
               "Select the source account and destination account",
               "Enter the transfer amount in KES",
               "Add a reference number and description for the transfer",
               "Click 'Save' — transfer is created with DRAFT status",
               "Click 'Submit' — status changes to PENDING_APPROVAL",
               "The transfer goes through multi-level approval based on the amount range settings",
               "Once all approvers have approved, status changes to APPROVED then DISBURSED"
             ],
             "tip": "You can view the approval progress by clicking 'Detail' on any PENDING_APPROVAL transfer. Approvers can add comments when approving or rejecting."}"""),

        // ── Credit ─────────────────────────────────────────────────
        Map.entry("set credit limit",
            """
            {"topic": "Manage Credit Limits",
             "steps": [
               "Open the sidebar and go to Finance → Credit Management",
               "The table shows all customers with their credit limit, utilization %, grade (A–F), and status",
               "Grades: A (0% utilized, green), B (0–30%, blue), C (30–60%, yellow), D (60–85%, orange), F (>85%, red)",
               "To view the AI credit score for a customer, go to AI Intelligence → Credit Evaluations",
               "Click a customer in Credit Evaluations to see the LLM reasoning, score breakdown, and recommended limit",
               "To adjust a credit limit manually, click the customer row → 'Adjust Limit', enter the new amount and justification",
               "Limits above KES 50,000 require Admin approval via the Approvals module"
             ],
             "tip": "The AI credit scorer runs automatically when new customers are created and periodically for existing customers. The recommended limit is advisory — Finance makes the final decision."}"""),

        // ── POS ─────────────────────────────────────────────────────
        Map.entry("pos sale",
            """
            {"topic": "Process a POS Sale",
             "steps": [
               "Open the sidebar and go to Sales → Point of Sale",
               "On the POS landing page, click to start a new sale",
               "Select your branch location from the dropdown at the top (required before adding products)",
               "Browse products by category on the left panel, or use the search bar in the products table",
               "Click 'Add' next to a product to add it to the cart (right panel)",
               "Adjust quantities using the +/- buttons in the cart, or type the quantity directly",
               "To link the sale to a customer account, toggle 'Customer on Account' and search for the customer",
               "Apply a discount per line item if needed",
               "Add a narration note if required",
               "Click 'Pay Now' to open the payment dialog",
               "Select payment method (Cash, M-Pesa, KCB, Card), enter the amount, and click 'Process Payment'",
               "A receipt is generated — click 'Print Bill' to print or 'Print Later' to defer",
               "Stock is deducted automatically and an invoice is created"
             ]}"""),

        // ── Deliveries / Route Planning ────────────────────────────
        Map.entry("create delivery",
            """
            {"topic": "Create a Delivery Route",
             "steps": [
               "Open the sidebar and go to AI Intelligence → Route Planner",
               "The route planner uses the Timefold optimization solver to generate the most efficient delivery routes",
               "Click 'Optimize Routes' — the AI calculates optimal stop sequences based on location, vehicle capacity, and time windows",
               "Review the generated routes on the map and in the route list",
               "Assign a driver and vehicle to each route",
               "Confirm and activate the routes — drivers see their assigned routes on their Dashboard",
               "Drivers follow the stop sequence and mark stops as delivered",
               "Completed routes appear in Route History at AI Intelligence → Route Planner → History"
             ],
             "tip": "Route optimization minimizes total distance while respecting vehicle load capacity. The system uses road network data for accurate distance calculations."}"""),

        // ── Reports ───────────────────────────────────────────────
        Map.entry("generate report",
            """
            {"topic": "Generate an AI Report",
             "steps": [
               "Option 1 — Via AI chat: type 'Generate a sales report' (or inventory, payment, credit risk, demand forecast, compliance, etc.) in this chat",
               "Option 2 — Via the Reports page: open the sidebar and go to AI Intelligence → AI Reports",
               "On the AI Reports page, select a report template (Sales Performance, Inventory Health, Payment & Collections, Credit Risk, Demand Forecast, Compliance)",
               "Set the time period (default is last 30 days)",
               "Click 'Generate' — the AI fetches live data using the business tools and builds the report section by section",
               "The report is formatted as a structured document with executive summary, data analysis, and recommendations",
               "Download or share the report as needed"
             ],
             "tip": "Asking in the chat is the fastest way. Just say: 'Give me a full business report', 'Generate a credit risk report', or 'Show me a payment collections summary for the last 7 days'."}"""),

        // ── Approvals ─────────────────────────────────────────────
        Map.entry("approvals",
            """
            {"topic": "Manage Approvals",
             "steps": [
               "Open the sidebar and go to Approvals",
               "The Approvals queue shows all items pending your review: expenses, funds transfers, credit limit increases, purchase requisitions, stock transfers",
               "Click an item to view its details and supporting information",
               "Click 'Approve' to approve or 'Reject' to reject (rejection requires a reason)",
               "Approved items automatically advance to the next approval level if multi-level approval is configured",
               "You receive a notification when items are assigned to you for approval"
             ]}"""),

        // ── AI Features ───────────────────────────────────────────
        Map.entry("anomaly alerts",
            """
            {"topic": "View Anomaly Alerts",
             "steps": [
               "Open the sidebar and go to AI Intelligence → Anomaly Alerts",
               "Alerts are color-coded by severity: CRITICAL (red), HIGH (orange), MEDIUM (yellow), LOW (blue)",
               "Alert types include: payment anomalies (unusual amounts or timing), inventory shrinkage (unexplained stock losses), data quality issues",
               "Click an alert row to view the full details including what triggered the alert and the AI's reasoning",
               "Review the alert and take corrective action",
               "Mark the alert as resolved once you have investigated and addressed the issue"
             ]}"""),

        Map.entry("demand forecast",
            """
            {"topic": "View Demand Forecasts",
             "steps": [
               "Open the sidebar and go to AI Intelligence → Demand Forecasts",
               "The list shows AI-predicted demand per customer-product combination for upcoming days",
               "Each forecast shows: customer name, product, predicted quantity, forecast date, and confidence range",
               "Go to AI Intelligence → Demand Forecasts → Order Suggestions to see AI-recommended order quantities for each customer",
               "Use the suggestions when creating orders to ensure customers receive optimal stock levels",
               "Filter by customer or product to focus on specific segments"
             ],
             "tip": "During the SYNTHETIC phase, forecast accuracy is lower. As more real transaction data accumulates, the XGBoost model improves automatically."}""")
    );

    @Tool("Get step-by-step instructions for how to perform actions in Zuqi. " +
          "Use this when the user asks 'how do I...', 'how to...', 'steps to...', " +
          "'guide me through...', 'where do I go to...', 'how can I...', " +
          "or any question about navigating or performing an action in the Zuqi system. " +
          "Parameter: action — describe what the user wants to do " +
          "(e.g. 'create order', 'record payment', 'check stock', 'process a POS sale').")
    public String getHowTo(
            @P("The action the user wants to perform, e.g. 'create order', 'record payment', 'check stock'")
            String action) {

        log.info("[TOOL CALLED] getHowTo action='{}'", action);

        if (action == null || action.isBlank()) {
            return availableGuides();
        }

        String normalized = action.toLowerCase().trim()
                .replaceAll("^(how (do i|to|can i|should i) )", "")
                .replaceAll("^(steps (to|for) |guide (for|to|me through) |help (with|me) |where do i |walk me through )", "")
                .replaceAll("\\s+(an?|the)\\s+", " ")
                .replaceAll("[?!.]$", "")
                .trim();

        // Exact match
        if (GUIDES.containsKey(normalized)) {
            return GUIDES.get(normalized);
        }

        // Substring match — guide key contained in query or vice versa
        for (Map.Entry<String, String> entry : GUIDES.entrySet()) {
            if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                return entry.getValue();
            }
        }

        // Keyword match — any word longer than 3 chars shared
        String[] queryWords = normalized.split("\\s+");
        for (Map.Entry<String, String> entry : GUIDES.entrySet()) {
            for (String word : queryWords) {
                if (word.length() > 3 && entry.getKey().contains(word)) {
                    return entry.getValue();
                }
            }
        }

        return availableGuides();
    }

    private String availableGuides() {
        return """
            {"message": "I can provide step-by-step instructions for any of these actions in Zuqi:",
             "available": [
               "create order", "create invoice / send invoice", "add customer", "view customers",
               "check stock", "add stock", "stock transfer",
               "create requisition", "create purchase order",
               "record payment", "record expense", "funds transfer",
               "set credit limit", "pos sale", "create delivery", "generate report",
               "approvals", "anomaly alerts", "demand forecast"
             ],
             "hint": "Try asking: 'How do I create an order?', 'Steps to record a payment', or 'How do I process a POS sale?'"}""";
    }
}
