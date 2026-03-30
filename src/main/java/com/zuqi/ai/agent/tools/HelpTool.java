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
             "tip": "During the SYNTHETIC phase, forecast accuracy is lower. As more real transaction data accumulates, the XGBoost model improves automatically."}"""),

        // ── Dispatch ──────────────────────────────────────────────
        Map.entry("dispatch",
            """
            {"topic": "Dispatch Orders",
             "steps": [
               "Open the sidebar and go to Sales → Dispatch",
               "The page shows all active orders across four statuses: CONFIRMED, PROCESSING, READY_FOR_DELIVERY, and OUT_FOR_DELIVERY",
               "For each order you can advance it to the next status using the action button on the row",
               "CONFIRMED → click 'Mark Ready' to move it to READY_FOR_DELIVERY",
               "READY_FOR_DELIVERY → click 'Dispatch' to move it to OUT_FOR_DELIVERY (goods leave the warehouse)",
               "OUT_FOR_DELIVERY → click 'Mark Delivered' once the customer has received the goods",
               "Status changes are instant — the customer's outstanding balance and stock levels update automatically"
             ],
             "tip": "Use Dispatch as your daily fulfilment board. Warehouse staff can work from READY_FOR_DELIVERY rows, drivers from OUT_FOR_DELIVERY rows."}"""),

        // ── Goods Receipts (GRN) ──────────────────────────────────
        Map.entry("receive goods",
            """
            {"topic": "Record a Goods Receipt (GRN)",
             "steps": [
               "Open the sidebar and go to Procurement → Goods Receipts",
               "The list shows all Goods Receipt Notes (GRNs) with their status: DRAFT, CONFIRMED, or REJECTED",
               "To create a new GRN, click the 'Create GRN' button",
               "Select the Purchase Order you are receiving against — the products and quantities pre-fill from the PO",
               "Select the destination warehouse",
               "Adjust received quantities if the delivery is partial (partial receipts are recorded and the PO remains open)",
               "Enter the supplier delivery note or reference number",
               "Click 'Save' to create the GRN in DRAFT status",
               "Review the GRN and click 'Confirm' to finalise — stock levels are updated immediately",
               "If goods are rejected or returned, click 'Reject' and add a reason"
             ],
             "tip": "A GRN is the formal record that goods arrived. Confirming a GRN updates inventory stock levels and marks the PO as RECEIVED (or PARTIALLY_RECEIVED for partial deliveries)."}"""),

        Map.entry("grn",
            """
            {"topic": "Goods Receipt Notes (GRN)",
             "steps": [
               "Open the sidebar and go to Procurement → Goods Receipts",
               "Filter by status (DRAFT / CONFIRMED / REJECTED) using the status dropdown",
               "Click any GRN row to view its full details, including individual line items received",
               "To create a new GRN, click 'Create GRN' — see 'receive goods' guide for full steps"
             ]}"""),

        // ── Data Import ───────────────────────────────────────────
        Map.entry("import data",
            """
            {"topic": "Import Data via CSV",
             "steps": [
               "Open the sidebar and go to Settings → Import Data (visible to ADMIN roles)",
               "Select the entity type you want to import: Customers, Suppliers, or Products",
               "Click 'Download Template' to get a CSV file with the required column headers and an example row",
               "Fill in the CSV file with your data — required columns vary by entity type",
               "Customers: businessName, ownerName, phone, email, nationalId, kraPin, address, city",
               "Suppliers: name, phone, email, kraPin, bankName, bankAccountNumber, bankAccountName",
               "Products: sku, name, categoryName, unitPrice, costPrice, unitOfMeasure, barcode",
               "Click 'Upload CSV' and select your completed file",
               "Click 'Import' — results show the number of records imported successfully and any row-level errors",
               "Fix errors in the CSV and re-upload the failed rows if needed"
             ],
             "tip": "The import is additive — it creates new records. Duplicate SKUs or phone numbers may cause row errors. Download the template first to ensure the correct column format."}"""),

        // ── Approval Thresholds ───────────────────────────────────
        Map.entry("approval thresholds",
            """
            {"topic": "Configure Approval Thresholds",
             "steps": [
               "Open the sidebar and go to Settings → Approval Thresholds (Admin only)",
               "Thresholds define how many approvals are required for transactions in a given amount range",
               "Workflow types: PURCHASE_REQUISITION, PURCHASE_ORDER, SALES_ORDER, PAYMENT_APPROVAL, CREDIT_LIMIT_CHANGE",
               "Click 'Add Threshold' to create a new rule",
               "Select the workflow type, enter the minimum amount (KES), optional maximum amount, and required number of approvals",
               "Example: a Purchase Requisition between KES 0–50,000 requires 1 approval; above KES 50,000 requires 2 approvals",
               "Click 'Save' — the new threshold takes effect immediately for all new transactions",
               "To edit an existing threshold, click the edit (pencil) icon on its row",
               "To remove a threshold, click the delete icon and confirm"
             ],
             "tip": "Thresholds are evaluated by amount range. Leave the maximum amount blank to create an open-ended upper threshold (catches all amounts above the minimum)."}"""),

        // ── AI: Stockout Predictions ──────────────────────────────
        Map.entry("stockout predictions",
            """
            {"topic": "View Stockout Predictions",
             "steps": [
               "Open the sidebar and go to AI Intelligence → Stockout Predictions",
               "The page shows XGBoost-powered predictions of which products are at risk of stocking out",
               "Each row shows: product name, warehouse, current stock level, predicted stockout risk, and days remaining",
               "Risk levels: HIGH (red — action needed now), MEDIUM (orange — monitor closely), LOW (green — sufficient stock)",
               "Click a product row to see the underlying prediction details and feature breakdown",
               "Use the predictions to prioritise purchase requisitions before stock runs out",
               "Click 'Reorder' on a high-risk row to start a purchase requisition directly from this page"
             ],
             "tip": "A SYNTHETIC badge means the model is still in early training. Predictions are advisory — cross-check against actual sales velocity before acting."}"""),

        // ── AI: Reorder Suggestions ───────────────────────────────
        Map.entry("reorder suggestions",
            """
            {"topic": "View and Approve Reorder Suggestions",
             "steps": [
               "Open the sidebar and go to AI Intelligence → Reorder Suggestions",
               "The page shows AI-generated suggestions for products that need to be reordered based on stock levels and demand forecasts",
               "Tabs: All Suggestions, Pending (awaiting approval), Approved, Below Reorder Level",
               "Each suggestion shows: product name, current stock, suggested order quantity, days until stockout, and supplier",
               "A coloured days-remaining badge indicates urgency: red (≤7 days), orange (≤14 days), green (safe)",
               "Click 'Approve' on a PENDING suggestion to accept it — this automatically creates a Purchase Requisition",
               "Approved suggestions move to the Approved tab; the linked requisition appears in Procurement → Requisitions",
               "Filter by the 'Below Reorder Level' tab to see products already below their configured reorder point"
             ],
             "tip": "Approving a reorder suggestion creates a Purchase Requisition automatically. You still need to submit the requisition for approval and convert it to a Purchase Order."}"""),

        // ── AI: Expiry Risk ───────────────────────────────────────
        Map.entry("expiry risk",
            """
            {"topic": "View Product Expiry Risk",
             "steps": [
               "Open the sidebar and go to AI Intelligence → Expiry Risk",
               "The page shows AI-scored expiry risk for all tracked product batches",
               "Filter by risk threshold using the dropdown: 30% (show all at-risk), 50% (moderate+), 70% (high+ only)",
               "Risk tiers: NORMAL (green), MODERATE (yellow), HIGH (orange), CRITICAL (red)",
               "Recommended actions per batch: NORMAL (no action), DISCOUNT (price markdown to move stock), REDISTRIBUTE (move to another location), QUARANTINE (isolate and investigate)",
               "Summary cards at the top show: total batches scored, high/critical count, discount recommendations, critical count",
               "Click a batch row to see the expiry date, days remaining, risk score, and AI recommendation",
               "Take action based on the recommendation — e.g. create a promotion/discount for DISCOUNT items, or raise an alert for QUARANTINE items"
             ],
             "tip": "Expiry risk is scored daily. CRITICAL batches expire within days — act immediately. DISCOUNT batches are within 30% of shelf life and should be promoted or transferred."}"""),

        // ── AI: Customer Analytics ────────────────────────────────
        Map.entry("customer analytics",
            """
            {"topic": "View AI Customer Analytics",
             "steps": [
               "Open the sidebar and go to AI Intelligence → Customer Analytics",
               "The page has two tabs: Segments and Churn Risk",
               "Segments tab: shows how your customers are distributed across 5 AI-assigned segments",
               "  HIGH_VALUE_GROWING (green) — top customers growing order volume",
               "  STABLE_MID_TIER (blue) — consistent mid-range buyers",
               "  NEW_LOW_ACTIVITY (amber) — recently acquired, low volume",
               "  AT_RISK_DECLINING (red) — previously active, declining orders",
               "  HIGH_VALUE_AT_RISK (purple) — high-value but showing churn signals",
               "Churn Risk tab: lists customers ranked by churn probability (0–100%)",
               "Each customer shows a Health Score (100 − churn probability) and tier: THRIVING (≥80), HEALTHY (≥65), NEEDS ATTENTION (≥45), AT RISK (≥25), CRITICAL (<25)",
               "Use this to prioritise sales rep outreach — focus on AT RISK and CRITICAL customers first"
             ],
             "tip": "Segment and churn data updates as the AI models are retrained. Use the segments to guide marketing campaigns and the churn list for proactive retention calls."}"""),

        // ── AI: Supplier Intelligence ─────────────────────────────
        Map.entry("supplier intelligence",
            """
            {"topic": "View Supplier Intelligence",
             "steps": [
               "Open the sidebar and go to AI Intelligence → Supplier Intelligence",
               "The page has two tabs: Supplier Risk and Price Trends",
               "Supplier Risk tab: scores each supplier on reliability, lead time, and quality",
               "Risk tiers: PREFERRED (green), RELIABLE (teal), ACCEPTABLE (blue), AT_RISK (orange), CRITICAL (red)",
               "Use this to prioritise which suppliers to build stronger relationships with and which to find alternatives for",
               "Price Trends tab: shows AI-detected price trends per product-supplier pair",
               "Trend directions: INCREASING (red — costs rising), DECREASING (green — favourable), STABLE (grey)",
               "Use price trend data to time purchase orders — buy ahead when prices are expected to increase"
             ],
             "tip": "Supplier risk scores factor in delivery history, quality issues, and payment terms. AT_RISK and CRITICAL suppliers should be reviewed — consider dual-sourcing critical products."}"""),

        // ── AI: Pricing Recommendations ───────────────────────────
        Map.entry("pricing recommendations",
            """
            {"topic": "View and Apply AI Pricing Recommendations",
             "steps": [
               "Open the sidebar and go to AI Intelligence → Pricing",
               "The page shows AI-generated price change recommendations for your products",
               "Tabs: Pending (awaiting review), Applied (accepted recommendations), Rejected",
               "Each recommendation shows: product name, current price, recommended price, % change, and estimated revenue impact",
               "Green revenue impact = expected revenue increase; red = expected decrease (e.g. strategic discount)",
               "To accept a recommendation, click 'Apply' on its card — the product's price is updated immediately",
               "To dismiss a recommendation without applying, click 'Reject'",
               "Applied and rejected recommendations move to their respective tabs for audit purposes"
             ],
             "tip": "Pricing recommendations are generated by the AI using demand elasticity and competitor price signals. Always review the revenue impact estimate before applying — the AI optimises for volume × margin."}"""),

        // ── AI: Cash Flow Forecast ────────────────────────────────
        Map.entry("cash flow forecast",
            """
            {"topic": "View Cash Flow Forecast",
             "steps": [
               "Open the sidebar and go to AI Intelligence → Cash Flow Forecast",
               "The page shows a bar chart of projected daily cash inflows (green) and outflows (red) with net position",
               "Select the forecast horizon using the toggle: 7 days, 30 days, or 90 days",
               "Each bar represents one day — positive net (green label) means expected net cash in, negative (red label) means net cash out",
               "Use the 7-day view for short-term liquidity planning",
               "Use the 30/90-day view to identify months with projected cash gaps and plan financing in advance",
               "Hover over any bar to see the breakdown: expected collections from customers, scheduled payments to suppliers, and operating expenses"
             ],
             "tip": "The forecast is driven by outstanding invoices (inflows) and upcoming purchase orders / expenses (outflows). Accuracy improves as more payment behaviour data accumulates."}"""),

        // ── Suppliers ─────────────────────────────────────────────
        Map.entry("add supplier",
            """
            {"topic": "Add a Supplier",
             "steps": [
               "Open the sidebar and go to Procurement → Suppliers",
               "Click the 'Add Supplier' button",
               "Fill in Basic Information: supplier name, phone number, email, KRA PIN, and registration number",
               "Fill in Location: city, county, and physical address",
               "Fill in Banking Details: bank name, account number, and account name",
               "Fill in Payment Terms: number of days credit is extended (e.g. 30 for Net-30)",
               "Click 'Save' to create the supplier"
             ],
             "tip": "Banking details are required to process supplier payments via funds transfer. KRA PIN is required for tax compliance on local Kenyan suppliers."}"""),

        Map.entry("view suppliers",
            """
            {"topic": "View and Manage Suppliers",
             "steps": [
               "Open the sidebar and go to Procurement → Suppliers",
               "The table lists all suppliers with their contact, location, and status",
               "Search by supplier name using the search bar",
               "Click a supplier row to view their full profile including purchase history",
               "To edit a supplier, click the edit icon on their row",
               "To create a new supplier, click 'Add Supplier'"
             ]}"""),

        // ── Sales Team ────────────────────────────────────────────
        Map.entry("sales team",
            """
            {"topic": "Manage Sales Team",
             "steps": [
               "Open the sidebar and go to Team Management → Sales Team",
               "The page shows all sales representatives with stat cards: Total, Active, Inactive",
               "Each row shows: name, contact details, distributor assignment, status, and AI Performance tier",
               "AI Performance tiers: EXCELLENT (green), GOOD (blue), AVERAGE (grey), AT_RISK (orange), CRITICAL (red)",
               "Click a rep row to view their detailed profile including assigned routes and order history",
               "To invite a new sales rep, click 'Invite Member' and fill in their details",
               "To edit a rep's details, click the edit icon on their row",
               "To remove a rep, click the delete icon and confirm"
             ],
             "tip": "The AI Performance score is calculated from order volume, collections rate, and customer satisfaction. AT_RISK and CRITICAL reps are flagged for coaching."}"""),

        // ── Warehouses ────────────────────────────────────────────
        Map.entry("warehouses",
            """
            {"topic": "Manage Warehouses",
             "steps": [
               "Open the sidebar and go to Inventory → Warehouses",
               "The page shows all warehouses with stat cards: Total, Active, Inactive",
               "Filter between ACTIVE and INACTIVE using the tabs; search by name, code, or city",
               "Each row shows: warehouse name, code, location (city), manager, and distributor",
               "Click the view icon to see stock levels and full details",
               "To add a new warehouse, click 'Add Warehouse' and fill in: name, code, city, address, and manager",
               "To edit, click the edit icon on the row"
             ]}"""),

        // ── Branches ─────────────────────────────────────────────
        Map.entry("branches",
            """
            {"topic": "Manage Branches",
             "steps": [
               "Open the sidebar and go to Inventory → Branches",
               "Branches represent physical sales or distribution locations (shops, outlets, depots)",
               "The table shows: name, branch code, city, status, and whether it is the HQ",
               "Filter between ACTIVE and INACTIVE branches using the tabs",
               "To create a branch, click 'Create Branch' and fill in: name, code, city, address, phone, email",
               "Check 'Headquarters' if this is the main location",
               "To switch your active branch (affects POS and stock operations), click 'Switch' on the branch row",
               "To view staff assigned to a branch, click 'View Users'",
               "To deactivate a branch, click the Deactivate action"
             ],
             "tip": "POS sales are scoped to the selected branch. Make sure you have switched to the correct branch before starting a POS session."}"""),

        // ── Inventory Batches ─────────────────────────────────────
        Map.entry("inventory batches",
            """
            {"topic": "View Inventory Batches",
             "steps": [
               "Open the sidebar and go to Inventory → Inventory Batches",
               "Batches track individual stock receipts for products with expiry dates (FMCG, perishables, pharmaceuticals)",
               "The table shows: product name, batch number, warehouse, quantity, cost price, expiry date, and status",
               "Status values: ACTIVE (in stock), DEPLETED (fully used), EXPIRED (past expiry date)",
               "Filter by warehouse or product to find specific batches",
               "Batches are created automatically when you receive goods via a GRN with an expiry date",
               "For expiry risk scoring, go to AI Intelligence → Expiry Risk"
             ],
             "tip": "FIFO (First In First Out) is applied automatically — oldest batches are consumed first to minimise expiry waste."}"""),

        // ── Stock Takes ───────────────────────────────────────────
        Map.entry("stock take",
            """
            {"topic": "Conduct a Stock Take",
             "steps": [
               "Open the sidebar and go to Inventory → Stock Takes",
               "Click 'New Stock Take', select the warehouse, add optional notes",
               "The stock take is created in DRAFT with all products pre-loaded",
               "Click 'View Details' to open the count sheet",
               "For each product, enter the physically counted quantity in the 'Counted Qty' column",
               "Variances are shown in real time: positive (green) = surplus, negative (red) = deficit",
               "Click 'Save' in the details dialog to record your counts",
               "Click 'Complete' when all products are counted — status changes to COMPLETED",
               "A Manager or Admin must click 'Approve' to finalise",
               "Once APPROVED, click 'Post' to adjust live inventory to match counted quantities"
             ],
             "tip": "Only POST after double-checking variances — posting permanently adjusts the stock ledger."}"""),

        // ── Users ─────────────────────────────────────────────────
        Map.entry("manage users",
            """
            {"topic": "Manage Users",
             "steps": [
               "Open the sidebar and go to Team Management → Users",
               "Stat cards show: Total, Active, Inactive users",
               "Search by name, email, phone, or filter by role using the dropdown",
               "Each row shows: name (with avatar), assigned roles (coloured chips), phone, last login, status",
               "To add a new user, click 'Add New User' and fill in details and role assignment",
               "To edit a user, click the view icon then edit their profile",
               "To deactivate a user (revoke access), click Deactivate and provide a reason",
               "Deactivated users appear in the Inactive tab and cannot log in",
               "Role changes take effect on the user's next login"
             ],
             "tip": "Tier roles (INITIATOR, VERIFIER, AUTHORIZER) are outlined in the chip display — these control approval workflow access for procurement and funds transfers."}"""),

        // ── Roles ─────────────────────────────────────────────────
        Map.entry("roles",
            """
            {"topic": "Manage Roles and Permissions",
             "steps": [
               "Open the sidebar and go to System Administration → Roles",
               "System roles (lock icon) are protected — custom roles (unlock icon) can be edited or deleted",
               "To create a custom role, click 'Create Role', give it a name and description",
               "In the Permissions step, expand each module accordion and tick permissions: Create, View, Update, Delete, Approve",
               "Use 'Select All' / 'Deselect All' per module for speed",
               "Click 'Save' — the role is immediately available to assign to users",
               "To edit permissions, click the edit icon; to delete a custom role, click delete",
               "Deleting a role removes it from all users assigned to it"
             ],
             "tip": "Permission changes on a role take effect on the user's next API request — no re-login required."}"""),

        // ── User Groups & Types ───────────────────────────────────
        Map.entry("user groups",
            """
            {"topic": "Manage User Groups",
             "steps": [
               "Open the sidebar and go to Team Management → User Groups",
               "User Groups assign a workflow tier (INITIATOR / VERIFIER / AUTHORIZER) to a team of users",
               "INITIATOR — creates and submits records; VERIFIER — reviews before final approval; AUTHORIZER — final sign-off",
               "To create a group, click 'New Group' and fill in: name, description, User Type, Workflow Tier",
               "For VERIFIER or AUTHORIZER, also set the Approval Level (1 = first in chain, 2 = second, etc.)",
               "Click 'Save', then assign users to this group from their user profile"
             ],
             "tip": "Funds Transfers and Purchase Requisitions use the approval levels set here. Higher level number = later stage in the approval chain."}"""),

        Map.entry("user types",
            """
            {"topic": "Manage User Types",
             "steps": [
               "Open the sidebar and go to Team Management → User Types",
               "User Types are permission templates that define what modules a class of user can access",
               "To create a user type, click 'New User Type'",
               "Step 1: Enter name and description",
               "Step 2: Expand each module accordion and check permissions (Create, View, Update, Delete, Approve)",
               "Use 'Select All' / 'Clear All' buttons per module for bulk selection",
               "Click 'Save' — the user type is now available to assign to User Groups"
             ],
             "tip": "User Types differ from Roles: Roles are system-level RBAC controls; User Types are organisational labels for grouping staff into approval workflow tiers."}"""),

        Map.entry("access control",
            """
            {"topic": "Access Control Overview",
             "steps": [
               "Zuqi has a two-layer access control system: Roles (system permissions) and User Groups (workflow tiers)",
               "Roles (System Administration → Roles): control what pages and actions a user can access",
               "User Types (Team Management → User Types): define permission templates for organisational categories",
               "User Groups (Team Management → User Groups): assign workflow tiers to staff groups for approval chains",
               "To restrict a user, edit their role assignment in Team Management → Users",
               "To configure approval chains, set up User Groups with the correct workflow tier and approval level",
               "All permission changes are recorded in Settings → Audit Logs"
             ]}"""),

        // ── Audit Logs ────────────────────────────────────────────
        Map.entry("audit logs",
            """
            {"topic": "View Audit Logs",
             "steps": [
               "Open the sidebar and go to Settings → Audit Logs",
               "Every significant action (CREATE / UPDATE / DELETE / APPROVE / REJECT / LOGIN etc.) is recorded",
               "Columns: User, Action, Entity, Module, Result (Success/Failed chip), IP Address, Date",
               "Filter by: Action type, Module (ORDERS / PAYMENTS / USERS / INVENTORY etc.), Result (Success / Failed)",
               "Failed actions (red chip) indicate blocked or errored attempts — useful for security investigation",
               "The audit log is read-only — no entries can be modified or deleted"
             ],
             "tip": "For KCB partnership compliance reviews, use the date filter + Module filter to export relevant sections."}"""),

        // ── Financial Overview ────────────────────────────────────
        Map.entry("financial overview",
            """
            {"topic": "View Financial Overview",
             "steps": [
               "Open the sidebar and go to Finance → Financial Overview",
               "Select a date range using the From and To date pickers, then click 'Generate'",
               "7 KPI cards: Revenue, Expenses, Net Income, Profit Margin %, AR Balance (receivables), AP Balance (payables), Cash Position",
               "Bar chart: Monthly Revenue vs Expenses side by side",
               "Pie chart: Expenses breakdown by category with percentages",
               "All values are in KES"
             ],
             "tip": "AR Balance = unpaid customer invoices. AP Balance = unpaid supplier bills. Net Income = Revenue minus Expenses."}"""),

        // ── Price Lists ───────────────────────────────────────────
        Map.entry("price lists",
            """
            {"topic": "Manage Price Lists",
             "steps": [
               "Open the sidebar and go to Products → Price Lists",
               "Price lists allow different pricing tiers for different customer groups (e.g. wholesale vs retail)",
               "Table columns: name, description, Default (chip), Status, Approval status, validity dates, item count",
               "To create a price list, click 'New Price List', enter name, description, and validity period",
               "Add products and set the price for each product in the list",
               "Submit for approval — price list activates once APPROVED",
               "Set a list as Default to apply it automatically to all new orders"
             ],
             "tip": "Non-default price lists must be selected manually at order creation. Only one Default list is active at a time."}"""),

        // ── Promotions ────────────────────────────────────────────
        Map.entry("promotions",
            """
            {"topic": "Manage Promotions",
             "steps": [
               "Open the sidebar and go to Products → Promotions",
               "Toggle 'Active only' to see only currently running promotions",
               "Promotion types: Percentage Discount, Fixed Amount Discount, Buy X Get Y",
               "Each row shows: name, type, discount value, minimum order amount, validity dates, status, approval",
               "To create, click 'New Promotion', fill in type, discount amount or %, applicable products, minimum order, and validity dates",
               "Promotions require approval before they activate",
               "To deactivate a running promotion early, click Deactivate on its row"
             ],
             "tip": "Promotions apply automatically at order creation when the cart meets the eligibility criteria."}"""),

        // ── Sales Returns ─────────────────────────────────────────
        Map.entry("sales returns",
            """
            {"topic": "Process a Sales Return",
             "steps": [
               "Open the sidebar and go to Sales → Sales Returns",
               "Sales returns record goods returned by customers with credit notes or refunds",
               "Status values: DRAFT, CONFIRMED, CANCELLED",
               "To create a return, click 'New Return'",
               "Select the original order, the products being returned, and the quantities",
               "Enter the reason for the return and the refund method (Credit Note, Cash, M-Pesa, Bank)",
               "Click 'Save' — return is created in DRAFT",
               "Click 'Confirm' to finalise — stock levels increase and a credit note is issued against the customer's account",
               "To cancel, click 'Cancel' on the DRAFT row"
             ],
             "tip": "Confirming a sales return increases inventory and creates a credit note reducing the customer's outstanding balance."}"""),

        // ── Purchase Returns ──────────────────────────────────────
        Map.entry("purchase returns",
            """
            {"topic": "Process a Purchase Return",
             "steps": [
               "Open the sidebar and go to Procurement → Purchase Returns",
               "Purchase returns record goods sent back to a supplier (damaged, wrong items, quality issues)",
               "Status values: DRAFT, CONFIRMED, CANCELLED",
               "To create, click 'New Return', select the GRN or PO, products, and quantities",
               "Enter the reason, click 'Save' — return is created in DRAFT",
               "Click 'Confirm' — stock levels decrease and a debit note is raised against the supplier",
               "To cancel, click 'Cancel' on the DRAFT row"
             ],
             "tip": "Confirming a purchase return reduces your inventory and reduces your AP balance for that supplier."}"""),

        // ── Reports ───────────────────────────────────────────────
        Map.entry("sales report",
            """
            {"topic": "View Sales Report",
             "steps": [
               "Open the sidebar and go to Analytics → Reports, then select Sales",
               "Set the date range",
               "4 KPI cards: Total Revenue (KES), Total Orders, Average Order Value, Active Sales Reps",
               "Orders table: order number, customer, date, payment status (PAID / PARTIALLY_PAID / PENDING / OVERDUE), amount",
               "Top Products Sold: product name, SKU, quantity sold, revenue",
               "Daily Breakdown: date, orders, revenue",
               "Sales Rep Performance: rep name, order count, total revenue"
             ]}"""),

        Map.entry("stock report",
            """
            {"topic": "View Stock Valuation Report",
             "steps": [
               "Open the sidebar and go to Analytics → Reports, then select Stock Valuation",
               "4 KPI cards: Total Products, Total Stock Value (KES), Low Stock count, Out of Stock count",
               "Warehouse Summary: each warehouse with product count and low-stock count",
               "Low Stock Items (yellow): products at or below reorder level — shows product, SKU, warehouse, qty, reorder level",
               "Out of Stock Items (red): products with zero stock",
               "Use this to prioritise procurement and identify stock gaps across warehouses"
             ]}"""),

        Map.entry("payment summary report",
            """
            {"topic": "View Payment Summary Report",
             "steps": [
               "Open the sidebar and go to Analytics → Reports, then select Payment Summary",
               "Set the date range and click 'Apply'",
               "4 KPI cards: Total Collected (KES), Outstanding Balance, Total Payments count, Unreconciled count",
               "By Payment Method: each method, transaction count, total collected",
               "Daily Collections: date, transaction count, amount collected"
             ]}"""),

        Map.entry("ar aging",
            """
            {"topic": "View AR Aging Report",
             "steps": [
               "Open the sidebar and go to Accounting → AR Aging",
               "AR Aging shows how long customer invoices have been outstanding",
               "Columns: customer name, current (0–30 days), 31–60 days, 61–90 days, over 90 days, total outstanding",
               "Red rows indicate invoices overdue by more than 90 days — priority for collections",
               "Sort by total outstanding to identify your largest debtors"
             ],
             "tip": "Customers in the 61–90 and 90+ columns should be considered for credit hold. Escalate to Finance for chronic late payers."}"""),

        Map.entry("ap aging",
            """
            {"topic": "View AP Aging Report",
             "steps": [
               "Open the sidebar and go to Accounting → AP Aging",
               "AP Aging shows how long your supplier bills have been outstanding",
               "Columns: supplier name, current (0–30 days), 31–60 days, 61–90 days, over 90 days, total payable",
               "Use this to plan outgoing payments and avoid late payment penalties or supplier relationship damage",
               "Sort by total payable to see which suppliers you owe the most"
             ]}"""),

        // ── General Ledger ────────────────────────────────────────
        Map.entry("general ledger",
            """
            {"topic": "General Ledger Overview",
             "steps": [
               "Open the sidebar and go to General Ledger — sub-sections: Accounts, Periods, Journal Entries, Cost Centers, Budgets, Reports",
               "Chart of Accounts (GL → Accounts): the master list of all ledger accounts (assets, liabilities, equity, revenue, expenses)",
               "GL Periods (GL → Periods): define accounting periods — periods must be OPEN to post entries",
               "Journal Entries (GL → Journal Entries): manual double-entry postings; click 'Create Journal' to post a debit/credit pair",
               "Cost Centers (GL → Cost Centers): department-level cost tracking — assign transactions for segment reporting",
               "Budgets (GL → Budgets): set revenue and expense budgets per account per period; variance shown in Budget Variance report",
               "GL Reports: Trial Balance, Budget Variance, General Ledger Detail, Balance Sheet, Profit & Loss, Cash Flow"
             ],
             "tip": "All financial transactions auto-post to the GL. Manual journal entries are for adjustments and corrections only."}"""),

        Map.entry("journal entry",
            """
            {"topic": "Create a Journal Entry",
             "steps": [
               "Open the sidebar and go to General Ledger → Journal Entries",
               "Click 'Create Journal'",
               "Select the GL period (must be OPEN), enter the date and a description",
               "Add debit lines: account + amount; add credit lines: account + matching amount",
               "Total debits must equal total credits before you can save",
               "Click 'Post' to finalise — posted journals cannot be edited, only reversed",
               "To reverse, open the posted journal and click 'Reverse' — creates an equal and opposite entry"
             ],
             "tip": "Common manual journal uses: month-end accruals, depreciation, error corrections. Most transactions post automatically."}"""),

        Map.entry("gl reports",
            """
            {"topic": "View General Ledger Reports",
             "steps": [
               "Open the sidebar and go to General Ledger → GL Reports",
               "Trial Balance: all accounts with debit/credit totals — verify debits equal credits for GL integrity",
               "General Ledger Detail: all transactions for a specific account over a selected period",
               "Budget Variance: actual vs budgeted amounts per account — shows over/under spend",
               "Balance Sheet: assets, liabilities, and equity at a point in time",
               "Profit & Loss: revenue minus expenses for a period (income statement)",
               "Cash Flow Statement: inflows and outflows by operating, investing, and financing activities",
               "For all reports: select the GL period or date range and click Generate"
             ]}"""),

        // ── Accounting ────────────────────────────────────────────
        Map.entry("bank reconciliation",
            """
            {"topic": "Perform a Bank Reconciliation",
             "steps": [
               "Open the sidebar and go to Accounting → Bank Reconciliations",
               "Click 'New Reconciliation'",
               "Select the bank account, enter the statement date and the closing balance from your physical bank statement",
               "The Difference column shows any variance between the bank statement and the GL cash account",
               "Drag and drop or click to upload a photo of the physical bank statement",
               "Investigate any differences by matching individual transactions",
               "When fully matched, click 'Mark Reconciled' — status changes to RECONCILED",
               "In-progress reconciliations show status IN_PROGRESS"
             ],
             "tip": "Run monthly. Common differences: outstanding cheques, bank charges not yet recorded, timing differences on deposits."}"""),

        Map.entry("tax rates",
            """
            {"topic": "Manage Tax Rates",
             "steps": [
               "Open the sidebar and go to Accounting → Tax Rates",
               "Tax rates apply automatically to invoices and products at point of sale",
               "Table shows: tax code, name, rate (%), type (Percentage/Fixed), what it applies to, default status",
               "To add, click 'Add Tax Rate' and fill in: name (e.g. 'VAT 16%'), code (e.g. 'VAT16'), rate, type, and applies-to",
               "Check 'Default' to apply automatically; check 'Compound' if tax is calculated on top of another tax",
               "To edit, click the edit icon; to delete, click the delete icon"
             ],
             "tip": "Kenya standard VAT is 16%. Mark it as Default so it applies to all taxable products without manual selection."}"""),

        // ── Billing ───────────────────────────────────────────────
        Map.entry("billing",
            """
            {"topic": "Manage Billing Subscriptions",
             "steps": [
               "Open the sidebar and go to Billing → Subscriptions (Super Admin only)",
               "Shows all distributor subscriptions: package, status (ACTIVE/TRIAL/EXPIRED), enabled modules, and validity dates",
               "Enabled modules (shown as chips) control which sidebar sections the distributor's users can see",
               "To assign or change a package, click 'Assign Package' and select distributor and package",
               "To add/remove individual modules, click 'Add/Remove Modules' on a subscription row",
               "In the Manage Modules dialog, use checkboxes to toggle modules — use 'Select All' or 'Clear' for speed",
               "Click 'Save' — changes take effect immediately without requiring user re-login"
             ],
             "tip": "The 'ai' module must be enabled in the subscription for distributors to see AI Intelligence in their sidebar."}"""),

        // ── Payment Setup ─────────────────────────────────────────
        Map.entry("payment setup",
            """
            {"topic": "Configure Payment Methods",
             "steps": [
               "Open the sidebar and go to Payment Setup",
               "Supported payment integrations: M-Pesa (Safaricom), KCB Bank, Cash, Bank Transfer",
               "To configure M-Pesa: click 'Configure' on the M-Pesa card and enter your Safaricom API consumer key, consumer secret, shortcode, and passkey",
               "To configure KCB: click 'Configure' on the KCB card and enter your KCB account number and API credentials",
               "After configuring, click 'Activate' to enable the payment method",
               "To temporarily disable a method, click 'Deactivate'",
               "Changes take effect immediately for all new transactions"
             ],
             "tip": "M-Pesa STK Push (customer pays via phone prompt) and M-Pesa C2B (till/paybill) are both supported. Contact your system admin for which mode is active."}"""),

        Map.entry("kcb config",
            """
            {"topic": "View KCB Bank Configurations",
             "steps": [
               "Open the sidebar and go to Payment Setup → KCB Configs (Admin only)",
               "Shows all KCB bank account configurations across merchants/distributors",
               "Columns: Business name, Config name, Account Number, Configured By, Date Set Up, Status (Active/Inactive)",
               "To activate a config, click 'Activate' on the row; to deactivate, click 'Deactivate'",
               "Only one KCB config can be ACTIVE per merchant at a time",
               "To add a new config, go to Payment Setup → Configure → KCB"
             ],
             "tip": "KCB configurations are required for KCB loan disbursements and collections. Contact your KCB relationship manager for API credentials."}"""),

        // ── Funds Transfer Amount Ranges ──────────────────────────
        Map.entry("funds transfer amount ranges",
            """
            {"topic": "Configure Funds Transfer Amount Ranges",
             "steps": [
               "Open the sidebar and go to Finance → Funds Transfers → Amount Ranges",
               "Amount Ranges define multi-level approval chains based on the transfer amount",
               "Each range has: name, minimum amount (KES), optional maximum amount, required approval levels, and named approvers",
               "Example: 'Small Transfers' KES 0–50,000 needs 1 approver; 'Large Transfers' KES 50,001+ needs 2 approvers",
               "To create, click 'Add Amount Range' and fill in name, min/max amounts, required level count",
               "Add each approval level: level number, level name (e.g. 'Finance Manager'), and the assigned approver user",
               "Click 'Save' — applies immediately to all new funds transfers",
               "To edit, click the edit icon; to delete, click the trash icon"
             ],
             "tip": "Ensure ranges are contiguous — no gaps. Leave max amount blank for the highest tier to catch all amounts above the minimum."}"""),

        // ── KYC Applications ──────────────────────────────────────
        Map.entry("kyc",
            """
            {"topic": "Manage KYC Applications",
             "steps": [
               "Open the sidebar and go to System Administration → KYC Applications (Admin/Super Admin only)",
               "KYC (Know Your Customer) applications are submitted by new merchants during onboarding",
               "Status values: SUBMITTED (pending review), APPROVED (identity verified), REJECTED",
               "Filter by status using the dropdown to see pending applications",
               "Click the view icon to open the KYC details side drawer with identity documents and business information",
               "To approve, click 'Approve' in the drawer — the merchant's account is activated",
               "To reject, click 'Reject' and enter a reason — the merchant is notified to resubmit"
             ],
             "tip": "Approved KYC is required for merchants to access credit facilities and high-value transaction limits."}"""),

        // ── Profile ───────────────────────────────────────────────
        Map.entry("profile",
            """
            {"topic": "Manage Your Profile",
             "steps": [
               "Click your name or avatar in the top navigation bar, then click 'Profile'",
               "The profile page has: Personal Information, Preferences, Notification Settings, and Security",
               "Click 'Edit Profile' to update: First Name, Last Name, Email, Phone, Language (English/Swahili), Timezone",
               "Toggle Notification Settings: Email, Push notifications, Order Updates, Payment Alerts",
               "To change your password, click 'Change Password' in Security — enter current password, new password, confirm",
               "To enable 2FA, click 'Enable 2FA', scan the QR code with your authenticator app, and enter the 6-digit code to verify",
               "Once 2FA is enabled, you need your authenticator app at every login"
             ],
             "tip": "Enable 2FA — it significantly reduces the risk of unauthorised access, especially for admin and finance roles."}"""),

        // ── AI: Recommendations ───────────────────────────────────
        Map.entry("ai recommendations",
            """
            {"topic": "View AI Operational Recommendations",
             "steps": [
               "Open the sidebar and go to AI Intelligence → AI Recommendations",
               "Summary cards: Pending (awaiting review), Accepted, Critical Priority (red), Estimated Total Impact (KES)",
               "Filter by category tab: All, Inventory, Credit, Sales, Operations",
               "Each card shows: suggestion text, category, priority (CRITICAL/HIGH/MEDIUM/LOW), estimated KES impact, and date",
               "To accept a recommendation, click 'Accept'",
               "To dismiss, click 'Reject' — a dialog asks for a reason",
               "Click 'Generate New' to trigger a fresh AI analysis",
               "Accepted and rejected recommendations are retained for audit"
             ],
             "tip": "Critical (red) recommendations are time-sensitive — e.g. a customer about to breach their credit limit, a product at risk of stockout. Review these first."}"""),

        // ── AI: Driver Route View ─────────────────────────────────
        Map.entry("driver route",
            """
            {"topic": "Driver Delivery Route View",
             "steps": [
               "Drivers access their route from AI Intelligence → Route Planner → Driver View",
               "The page shows today's assigned route: total stops, distance, estimated time, and completion progress bar",
               "The 'Next Stop' card shows the next delivery: merchant name, address, ETA, and items to deliver",
               "Click 'Navigate' to open Google Maps with directions to that address",
               "After completing a delivery, click 'Complete Stop' — the next stop becomes active",
               "Completed stops appear at the bottom with strikethrough names",
               "When all stops are completed, a success confirmation is shown"
             ],
             "tip": "Complete stops in the displayed order — the route is AI-optimised for the most efficient sequence."}"""),

        // ── AI: System Health ─────────────────────────────────────
        Map.entry("ai system health",
            """
            {"topic": "View AI System Health",
             "steps": [
               "Open the sidebar and go to AI Intelligence → AI System Health (Admin only)",
               "The page shows the health status of all 15 AI models deployed in the system",
               "Each model card shows: model name, status (ACTIVE / TRAINING / RETIRED), data phase (SYNTHETIC / HYBRID / REAL), primary metric value (e.g. macro-F1, R², Silhouette), and last trained date",
               "Data phase badge: SYNTHETIC (blue) = model trained on synthetic data, HYBRID (purple) = mix, REAL (green) = trained on real business data",
               "To manually trigger a retraining run for a model, click 'Retrain' on its card",
               "The overall system health summary at the top shows active model count, last system-wide training date, and any models with degraded performance",
               "Models with a red status indicator have fallen below the minimum quality threshold and should be retrained"
             ],
             "tip": "Retraining is triggered automatically on a schedule. Manual retrain is useful after a large data import or when you see prediction quality degrading. Only ADMIN/AI Admin roles can trigger retraining."}""")
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
               "Sales: create order, create invoice, send invoice, dispatch orders, sales returns, pos sale",
               "Customers: add customer, view customers, set credit limit",
               "Products: check stock, add stock, stock transfer, price lists, promotions",
               "Inventory: inventory batches, stock take, warehouses, branches",
               "Procurement: create requisition, create purchase order, receive goods (GRN), add supplier, view suppliers, purchase returns",
               "Finance: record payment, record expense, funds transfer, funds transfer amount ranges, financial overview",
               "Accounting: bank reconciliation, tax rates, ar aging, ap aging, general ledger, journal entry, gl reports",
               "Team & Access: manage users, roles, user groups, user types, access control, sales team",
               "System Admin: audit logs, import data, billing, kyc, kcb config, payment setup",
               "Settings: approval thresholds, profile",
               "AI Intelligence: anomaly alerts, demand forecast, stockout predictions, reorder suggestions, expiry risk, customer analytics, supplier intelligence, pricing recommendations, cash flow forecast, ai recommendations, create delivery (route planning), driver route, generate report, ai system health"
             ],
             "hint": "Try asking: 'How do I create an order?', 'Steps to receive goods against a PO', 'How do I dispatch an order?', 'How do I do a stock take?', or 'How do I view expiry risk?'"}""";
    }
}
