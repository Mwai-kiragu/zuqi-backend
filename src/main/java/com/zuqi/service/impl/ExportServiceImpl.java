package com.zuqi.service.impl;

import com.zuqi.domain.branch.DistributorBranch;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.expense.Expense;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.payment.Payment;
import com.zuqi.domain.pos.PosSale;
import com.zuqi.domain.pricing.PriceList;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.product.ProductCategory;
import com.zuqi.domain.supplier.Supplier;
import com.zuqi.domain.user.User;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.DistributorBranchRepository;
import com.zuqi.repository.ExpenseRepository;
import com.zuqi.repository.InvoiceRepository;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.PaymentRepository;
import com.zuqi.repository.PosSaleRepository;
import com.zuqi.repository.PriceListRepository;
import com.zuqi.repository.ProductCategoryRepository;
import com.zuqi.repository.ProductRepository;
import com.zuqi.repository.StockRepository;
import com.zuqi.repository.SupplierRepository;
import com.zuqi.repository.WarehouseRepository;
import com.zuqi.service.EmailService;
import com.zuqi.service.ExportService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportServiceImpl implements ExportService {

    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final InvoiceRepository invoiceRepository;
    private final WarehouseRepository warehouseRepository;
    private final DistributorBranchRepository branchRepository;
    private final ProductCategoryRepository categoryRepository;
    private final PosSaleRepository posSaleRepository;
    private final ExpenseRepository expenseRepository;
    private final PriceListRepository priceListRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final EmailService emailService;
    private final SecurityUtils securityUtils;

    @Override
    @Async
    public void exportCustomersToEmail() {
        User user = securityUtils.getCurrentUser();
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();

        List<Customer> customers;
        if (merchantId != null) {
            customers = customerRepository.findByDistributorMerchantId(merchantId);
        } else if (distributorId != null) {
            customers = customerRepository.findByDistributorId(distributorId);
        } else {
            customers = customerRepository.findAll();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Business Name,Owner Name,Phone,Email,National ID,KRA PIN,Address,City,Credit Limit,Status\n");
        for (Customer c : customers) {
            csv.append(escape(c.getBusinessName())).append(',')
               .append(escape(c.getOwnerName())).append(',')
               .append(escape(c.getPhone())).append(',')
               .append(escape(c.getEmail())).append(',')
               .append(escape(c.getNationalId())).append(',')
               .append(escape(c.getKraPin())).append(',')
               .append(escape(c.getAddress())).append(',')
               .append(escape(c.getCity())).append(',')
               .append(c.getCreditLimit() != null ? c.getCreditLimit() : 0).append(',')
               .append(c.isActive() ? "Active" : "Inactive").append('\n');
        }

        emailService.sendDataExportEmail(
            user.getEmail(), user.getFirstName(),
            "Customers", customers.size(), csv.toString(), "customers_export.csv"
        );
        log.info("Customers export email sent to {} — {} records", user.getEmail(), customers.size());
    }

    @Override
    @Async
    public void exportSuppliersToEmail() {
        User user = securityUtils.getCurrentUser();
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();

        List<Supplier> suppliers;
        if (merchantId != null) {
            suppliers = supplierRepository.findByDistributorMerchantId(merchantId);
        } else if (distributorId != null) {
            suppliers = supplierRepository.findByDistributorId(distributorId);
        } else {
            suppliers = supplierRepository.findAll();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Code,Name,Phone,Email,KRA PIN,Bank Name,Account Number,Account Name,Status\n");
        for (Supplier s : suppliers) {
            csv.append(escape(s.getSupplierCode())).append(',')
               .append(escape(s.getName())).append(',')
               .append(escape(s.getPhone())).append(',')
               .append(escape(s.getEmail())).append(',')
               .append(escape(s.getKraPin())).append(',')
               .append(escape(s.getBankName())).append(',')
               .append(escape(s.getBankAccountNumber())).append(',')
               .append(escape(s.getBankAccountName())).append(',')
               .append(s.isBlacklisted() ? "Blacklisted" : s.isActive() ? "Active" : "Inactive")
               .append('\n');
        }

        emailService.sendDataExportEmail(
            user.getEmail(), user.getFirstName(),
            "Suppliers", suppliers.size(), csv.toString(), "suppliers_export.csv"
        );
        log.info("Suppliers export email sent to {} — {} records", user.getEmail(), suppliers.size());
    }

    @Override
    @Async
    public void exportProductsToEmail() {
        User user = securityUtils.getCurrentUser();
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();

        List<Product> products;
        if (merchantId != null) {
            products = productRepository.findByDistributorMerchantIdAndActiveTrue(merchantId);
        } else if (distributorId != null) {
            products = productRepository.findByDistributorIdAndActiveTrue(distributorId);
        } else {
            products = productRepository.findAll();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("SKU,Name,Category,Unit Price,Cost Price,Unit of Measure,Barcode,Status\n");
        for (Product p : products) {
            csv.append(escape(p.getSku())).append(',')
               .append(escape(p.getName())).append(',')
               .append(escape(p.getCategory() != null ? p.getCategory().getName() : "")).append(',')
               .append(p.getUnitPrice() != null ? p.getUnitPrice() : 0).append(',')
               .append(p.getCostPrice() != null ? p.getCostPrice() : "").append(',')
               .append(escape(p.getUnitOfMeasure())).append(',')
               .append(escape(p.getBarcode())).append(',')
               .append(p.isActive() ? "Active" : "Inactive").append('\n');
        }

        emailService.sendDataExportEmail(
            user.getEmail(), user.getFirstName(),
            "Products", products.size(), csv.toString(), "products_export.csv"
        );
        log.info("Products export email sent to {} — {} records", user.getEmail(), products.size());
    }

    @Override
    @Async
    public void exportInventoryToEmail() {
        User user = securityUtils.getCurrentUser();
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();

        List<Stock> stocks;
        if (merchantId != null) {
            stocks = stockRepository.findAllByMerchantIdFetched(merchantId);
        } else if (distributorId != null) {
            stocks = stockRepository.findAllByDistributorIdFetched(distributorId);
        } else {
            stocks = stockRepository.findAllFetched();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Warehouse,Product,SKU,Quantity,Reserved Qty,Reorder Level\n");
        for (Stock s : stocks) {
            csv.append(escape(s.getWarehouse() != null ? s.getWarehouse().getName() : "")).append(',')
               .append(escape(s.getProduct() != null ? s.getProduct().getName() : "")).append(',')
               .append(escape(s.getProduct() != null ? s.getProduct().getSku() : "")).append(',')
               .append(s.getQuantity() != null ? s.getQuantity() : 0).append(',')
               .append(s.getReservedQuantity() != null ? s.getReservedQuantity() : 0).append(',')
               .append(s.getReorderLevel() != null ? s.getReorderLevel() : "").append('\n');
        }

        emailService.sendDataExportEmail(
            user.getEmail(), user.getFirstName(),
            "Inventory", stocks.size(), csv.toString(), "inventory_export.csv"
        );
        log.info("Inventory export email sent to {} — {} records", user.getEmail(), stocks.size());
    }

    @Override
    @Async
    public void exportInvoicesToEmail() {
        User user = securityUtils.getCurrentUser();
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();

        List<Invoice> invoices;
        if (merchantId != null) {
            invoices = invoiceRepository.findAllByDistributorMerchantIdForExport(merchantId);
        } else if (distributorId != null) {
            invoices = invoiceRepository.findAllByDistributorIdForExport(distributorId);
        } else {
            invoices = invoiceRepository.findAllForExport();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Invoice #,Customer,Total,Paid,Balance Due,Status,Issue Date,Due Date\n");
        for (Invoice i : invoices) {
            csv.append(escape(i.getInvoiceNumber())).append(',')
               .append(escape(i.getMerchant() != null ? i.getMerchant().getBusinessName() : "")).append(',')
               .append(i.getTotalAmount() != null ? i.getTotalAmount() : 0).append(',')
               .append(i.getPaidAmount() != null ? i.getPaidAmount() : 0).append(',')
               .append(i.getBalanceDue() != null ? i.getBalanceDue() : 0).append(',')
               .append(i.getStatus() != null ? i.getStatus().name() : "").append(',')
               .append(i.getIssueDate() != null ? i.getIssueDate() : "").append(',')
               .append(i.getDueDate() != null ? i.getDueDate() : "").append('\n');
        }

        emailService.sendDataExportEmail(
            user.getEmail(), user.getFirstName(),
            "Invoices", invoices.size(), csv.toString(), "invoices_export.csv"
        );
        log.info("Invoices export email sent to {} — {} records", user.getEmail(), invoices.size());
    }

    @Override
    @Async
    public void exportWarehousesToEmail() {
        User user = securityUtils.getCurrentUser();
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();

        List<Warehouse> warehouses;
        if (merchantId != null) {
            warehouses = warehouseRepository.findByDistributorMerchantId(merchantId);
        } else if (distributorId != null) {
            warehouses = warehouseRepository.findByDistributorId(distributorId);
        } else {
            warehouses = warehouseRepository.findAll();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Code,Name,Address,City,Active\n");
        for (Warehouse w : warehouses) {
            csv.append(escape(w.getCode())).append(',')
               .append(escape(w.getName())).append(',')
               .append(escape(w.getAddress())).append(',')
               .append(escape(w.getCity())).append(',')
               .append(w.isActive() ? "Active" : "Inactive").append('\n');
        }

        emailService.sendDataExportEmail(
            user.getEmail(), user.getFirstName(),
            "Warehouses", warehouses.size(), csv.toString(), "warehouses_export.csv"
        );
        log.info("Warehouses export email sent to {} — {} records", user.getEmail(), warehouses.size());
    }

    @Override
    @Async
    public void exportBranchesToEmail() {
        User user = securityUtils.getCurrentUser();
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();

        List<DistributorBranch> branches;
        if (merchantId != null) {
            branches = branchRepository.findByDistributorMerchantId(merchantId);
        } else if (distributorId != null) {
            branches = branchRepository.findByDistributorId(distributorId);
        } else {
            branches = branchRepository.findAll();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Code,Name,Address,City,Phone,Email,Headquarters,Status\n");
        for (DistributorBranch b : branches) {
            csv.append(escape(b.getCode())).append(',')
               .append(escape(b.getName())).append(',')
               .append(escape(b.getAddress())).append(',')
               .append(escape(b.getCity())).append(',')
               .append(escape(b.getPhone())).append(',')
               .append(escape(b.getEmail())).append(',')
               .append(b.isHeadquarters() ? "Yes" : "No").append(',')
               .append(b.getStatus() != null ? b.getStatus().name() : "").append('\n');
        }

        emailService.sendDataExportEmail(
            user.getEmail(), user.getFirstName(),
            "Branches", branches.size(), csv.toString(), "branches_export.csv"
        );
        log.info("Branches export email sent to {} — {} records", user.getEmail(), branches.size());
    }

    @Override
    @Async
    public void exportCategoriesToEmail() {
        User user = securityUtils.getCurrentUser();
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();

        List<ProductCategory> categories;
        if (merchantId != null) {
            categories = categoryRepository.findByMerchantIdFetchedForExport(merchantId);
        } else if (distributorId != null) {
            categories = categoryRepository.findByDistributorIdFetchedForExport(distributorId);
        } else {
            categories = categoryRepository.findAll();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Name,Description,Parent Category,Active\n");
        for (ProductCategory c : categories) {
            csv.append(escape(c.getName())).append(',')
               .append(escape(c.getDescription())).append(',')
               .append(escape(c.getParent() != null ? c.getParent().getName() : "")).append(',')
               .append(c.isActive() ? "Active" : "Inactive").append('\n');
        }

        emailService.sendDataExportEmail(
            user.getEmail(), user.getFirstName(),
            "Product Categories", categories.size(), csv.toString(), "categories_export.csv"
        );
        log.info("Categories export email sent to {} — {} records", user.getEmail(), categories.size());
    }

    @Override
    @Async
    public void exportPosSalesToEmail() {
        User user = securityUtils.getCurrentUser();
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();

        List<PosSale> sales;
        if (merchantId != null) {
            sales = posSaleRepository.findAllByMerchantIdFetched(merchantId);
        } else if (distributorId != null) {
            sales = posSaleRepository.findAllByDistributorIdFetched(distributorId);
        } else {
            sales = posSaleRepository.findAllFetched();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Receipt #,Branch,Customer,Total Amount,Amount Paid,Status,Date\n");
        for (PosSale s : sales) {
            csv.append(escape(s.getReceiptNumber())).append(',')
               .append(escape(s.getBranch() != null ? s.getBranch().getName() : "")).append(',')
               .append(escape(s.getCustomerName())).append(',')
               .append(s.getTotalAmount() != null ? s.getTotalAmount() : 0).append(',')
               .append(s.getAmountPaid() != null ? s.getAmountPaid() : 0).append(',')
               .append(s.getStatus() != null ? s.getStatus().name() : "").append(',')
               .append(s.getCreatedAt() != null ? s.getCreatedAt().toLocalDate() : "").append('\n');
        }

        emailService.sendDataExportEmail(
            user.getEmail(), user.getFirstName(),
            "POS Sales", sales.size(), csv.toString(), "pos_sales_export.csv"
        );
        log.info("POS sales export email sent to {} — {} records", user.getEmail(), sales.size());
    }

    @Override
    @Async
    public void exportFinancialReportToEmail() {
        User user = securityUtils.getCurrentUser();
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();

        List<Expense> expenses;
        if (merchantId != null) {
            expenses = expenseRepository.findByDistributorMerchantIdForExport(merchantId);
        } else if (distributorId != null) {
            expenses = expenseRepository.findByDistributorIdOrderByExpenseDateDesc(distributorId);
        } else {
            expenses = expenseRepository.findAll();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Ref #,Title,Category,Amount,Expense Date,Status,Payment Method\n");
        for (Expense e : expenses) {
            csv.append(escape(e.getReferenceNumber())).append(',')
               .append(escape(e.getTitle())).append(',')
               .append(e.getCategory() != null ? e.getCategory().name() : "").append(',')
               .append(e.getAmount() != null ? e.getAmount() : 0).append(',')
               .append(e.getExpenseDate() != null ? e.getExpenseDate() : "").append(',')
               .append(e.getStatus() != null ? e.getStatus().name() : "").append(',')
               .append(escape(e.getPaymentMethod())).append('\n');
        }

        emailService.sendDataExportEmail(
            user.getEmail(), user.getFirstName(),
            "Financial Report", expenses.size(), csv.toString(), "financial_report_export.csv"
        );
        log.info("Financial report export email sent to {} — {} records", user.getEmail(), expenses.size());
    }

    @Override
    @Async
    public void exportPriceListsToEmail() {
        User user = securityUtils.getCurrentUser();
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();

        List<PriceList> priceLists;
        if (merchantId != null) {
            priceLists = priceListRepository.findAllByDistributorMerchantId(merchantId);
        } else if (distributorId != null) {
            priceLists = priceListRepository.findAllByDistributorId(distributorId);
        } else {
            priceLists = priceListRepository.findAll();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Name,Description,Default,Status,Approval Status,Valid From,Valid To,Items Count\n");
        for (PriceList pl : priceLists) {
            csv.append(escape(pl.getName())).append(',')
               .append(escape(pl.getDescription())).append(',')
               .append(pl.isDefault() ? "Yes" : "No").append(',')
               .append(pl.isActive() ? "Active" : "Inactive").append(',')
               .append(escape(pl.getApprovalStatus())).append(',')
               .append(pl.getValidFrom() != null ? pl.getValidFrom() : "").append(',')
               .append(pl.getValidTo() != null ? pl.getValidTo() : "").append(',')
               .append(pl.getItems().size()).append('\n');
        }

        emailService.sendDataExportEmail(
            user.getEmail(), user.getFirstName(),
            "Price Lists", priceLists.size(), csv.toString(), "price_lists_export.csv"
        );
        log.info("Price lists export email sent to {} — {} records", user.getEmail(), priceLists.size());
    }

    @Override
    @Async
    public void exportOrdersToEmail() {
        User user = securityUtils.getCurrentUser();
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();

        List<Order> orders;
        if (merchantId != null) {
            orders = orderRepository.findByDistributorMerchantIdOrderByCreatedAtDesc(merchantId);
        } else if (distributorId != null) {
            orders = orderRepository.findByDistributorIdOrderByCreatedAtDesc(distributorId);
        } else {
            orders = orderRepository.findAll(org.springframework.data.domain.Sort.by("createdAt").descending());
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Order #,Customer,Total (KES),Paid (KES),Payment Status,Order Status,Date\n");
        for (Order o : orders) {
            csv.append(escape(o.getOrderNumber())).append(',')
               .append(escape(o.getMerchant() != null ? o.getMerchant().getBusinessName() : "Walk In")).append(',')
               .append(o.getTotalAmount() != null ? o.getTotalAmount() : 0).append(',')
               .append(o.getPaidAmount() != null ? o.getPaidAmount() : 0).append(',')
               .append(o.getPaymentStatus() != null ? o.getPaymentStatus().name() : "").append(',')
               .append(o.getStatus() != null ? o.getStatus().name() : "").append(',')
               .append(o.getCreatedAt() != null ? o.getCreatedAt().toLocalDate() : "").append('\n');
        }

        emailService.sendDataExportEmail(
            user.getEmail(), user.getFirstName(),
            "Orders", orders.size(), csv.toString(), "orders_export.csv"
        );
        log.info("Orders export email sent to {} — {} records", user.getEmail(), orders.size());
    }

    @Override
    @Async
    public void exportPaymentsToEmail() {
        User user = securityUtils.getCurrentUser();
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();

        List<Payment> payments;
        if (merchantId != null) {
            payments = paymentRepository.findByDistributorMerchantIdOrderByCreatedAtDesc(merchantId);
        } else if (distributorId != null) {
            payments = paymentRepository.findByDistributorIdOrderByCreatedAtDesc(distributorId);
        } else {
            payments = paymentRepository.findAll(org.springframework.data.domain.Sort.by("createdAt").descending());
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Payment #,Customer,Order #,Amount (KES),Method,Status,Date\n");
        for (Payment p : payments) {
            csv.append(escape(p.getPaymentNumber())).append(',')
               .append(escape(p.getMerchant() != null ? p.getMerchant().getBusinessName() : "")).append(',')
               .append(escape(p.getOrder() != null ? p.getOrder().getOrderNumber() : "")).append(',')
               .append(p.getAmount() != null ? p.getAmount() : 0).append(',')
               .append(escape(p.getPaymentMethod() != null ? p.getPaymentMethod().getName() : "")).append(',')
               .append(p.getStatus() != null ? p.getStatus().name() : "").append(',')
               .append(p.getPaymentDate() != null ? p.getPaymentDate().toLocalDate()
                       : p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : "").append('\n');
        }

        emailService.sendDataExportEmail(
            user.getEmail(), user.getFirstName(),
            "Payments", payments.size(), csv.toString(), "payments_export.csv"
        );
        log.info("Payments export email sent to {} — {} records", user.getEmail(), payments.size());
    }

    /** CSV-safe quoting: wrap value in quotes if it contains comma, quote, or newline */
    private String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
