package com.zuqi.service.impl;

import com.opencsv.CSVReader;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.product.ProductCategory;
import com.zuqi.domain.supplier.Supplier;
import com.zuqi.repository.*;
import com.zuqi.service.ImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportServiceImpl implements ImportService {

    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final DistributorRepository distributorRepository;
    private final ProductCategoryRepository productCategoryRepository;

    @Override
    @Transactional
    public ImportResult importCustomers(MultipartFile file, UUID distributorId) {
        Distributor distributor = distributorRepository.findById(distributorId).orElse(null);
        List<RowError> errors = new ArrayList<>();
        int imported = 0;
        int rowNum = 1; // header is row 0

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String[] headers = reader.readNext(); // skip header
            if (headers == null) return new ImportResult(0, 0, errors);

            String[] row;
            while ((row = reader.readNext()) != null) {
                rowNum++;
                try {
                    if (row.length < 3) {
                        errors.add(new RowError(rowNum, "Row has too few columns"));
                        continue;
                    }
                    String businessName = trim(row, 0);
                    String ownerName = trim(row, 1);
                    String phone = trim(row, 2);
                    String email = trim(row, 3);
                    String nationalId = trim(row, 4);
                    String kraPin = trim(row, 5);
                    String address = trim(row, 6);
                    String city = trim(row, 7);

                    if (businessName.isEmpty()) {
                        errors.add(new RowError(rowNum, "businessName is required"));
                        continue;
                    }
                    if (phone.isEmpty()) {
                        errors.add(new RowError(rowNum, "phone is required"));
                        continue;
                    }
                    if (customerRepository.existsByPhone(phone)) {
                        errors.add(new RowError(rowNum, "Customer with phone " + phone + " already exists"));
                        continue;
                    }

                    Customer customer = Customer.builder()
                            .customerCode(generateCode("CUST", customerRepository.count()))
                            .businessName(businessName)
                            .ownerName(ownerName.isEmpty() ? null : ownerName)
                            .phone(phone)
                            .email(email.isEmpty() ? null : email)
                            .nationalId(nationalId.isEmpty() ? null : nationalId)
                            .kraPin(kraPin.isEmpty() ? null : kraPin)
                            .address(address.isEmpty() ? null : address)
                            .city(city.isEmpty() ? null : city)
                            .distributor(distributor)
                            .approvalStatus("APPROVED")
                            .build();

                    customerRepository.save(customer);
                    imported++;
                } catch (Exception e) {
                    errors.add(new RowError(rowNum, e.getMessage()));
                }
            }
        } catch (Exception e) {
            log.error("Error reading CSV file for customer import: {}", e.getMessage());
            errors.add(new RowError(0, "Failed to read file: " + e.getMessage()));
        }

        return new ImportResult(imported, errors.size(), errors);
    }

    @Override
    @Transactional
    public ImportResult importSuppliers(MultipartFile file, UUID distributorId) {
        Distributor distributor = distributorRepository.findById(distributorId).orElse(null);
        List<RowError> errors = new ArrayList<>();
        int imported = 0;
        int rowNum = 1;

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String[] headers = reader.readNext();
            if (headers == null) return new ImportResult(0, 0, errors);

            String[] row;
            while ((row = reader.readNext()) != null) {
                rowNum++;
                try {
                    if (row.length < 2) {
                        errors.add(new RowError(rowNum, "Row has too few columns"));
                        continue;
                    }
                    String name = trim(row, 0);
                    String phone = trim(row, 1);
                    String email = trim(row, 2);
                    String kraPin = trim(row, 3);
                    String bankName = trim(row, 4);
                    String bankAccountNumber = trim(row, 5);
                    String bankAccountName = trim(row, 6);

                    if (name.isEmpty()) {
                        errors.add(new RowError(rowNum, "name is required"));
                        continue;
                    }
                    if (phone.isEmpty()) {
                        errors.add(new RowError(rowNum, "phone is required"));
                        continue;
                    }
                    if (kraPin.isEmpty()) {
                        errors.add(new RowError(rowNum, "kraPin is required"));
                        continue;
                    }
                    if (supplierRepository.existsByPhone(phone)) {
                        errors.add(new RowError(rowNum, "Supplier with phone " + phone + " already exists"));
                        continue;
                    }
                    if (supplierRepository.existsByKraPin(kraPin)) {
                        errors.add(new RowError(rowNum, "Supplier with KRA PIN " + kraPin + " already exists"));
                        continue;
                    }

                    Supplier supplier = Supplier.builder()
                            .supplierCode(generateCode("SUP", supplierRepository.count()))
                            .name(name)
                            .phone(phone)
                            .email(email.isEmpty() ? null : email)
                            .kraPin(kraPin)
                            .bankName(bankName.isEmpty() ? null : bankName)
                            .bankAccountNumber(bankAccountNumber.isEmpty() ? null : bankAccountNumber)
                            .bankAccountName(bankAccountName.isEmpty() ? null : bankAccountName)
                            .distributor(distributor)
                            .approvalStatus("APPROVED")
                            .build();

                    supplierRepository.save(supplier);
                    imported++;
                } catch (Exception e) {
                    errors.add(new RowError(rowNum, e.getMessage()));
                }
            }
        } catch (Exception e) {
            log.error("Error reading CSV file for supplier import: {}", e.getMessage());
            errors.add(new RowError(0, "Failed to read file: " + e.getMessage()));
        }

        return new ImportResult(imported, errors.size(), errors);
    }

    @Override
    @Transactional
    public ImportResult importProducts(MultipartFile file, UUID distributorId) {
        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new RuntimeException("Distributor not found: " + distributorId));
        List<RowError> errors = new ArrayList<>();
        int imported = 0;
        int rowNum = 1;

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String[] headers = reader.readNext();
            if (headers == null) return new ImportResult(0, 0, errors);

            String[] row;
            while ((row = reader.readNext()) != null) {
                rowNum++;
                try {
                    if (row.length < 2) {
                        errors.add(new RowError(rowNum, "Row has too few columns"));
                        continue;
                    }
                    String sku = trim(row, 0);
                    String name = trim(row, 1);
                    String categoryName = trim(row, 2);
                    String unitPriceStr = trim(row, 3);
                    String costPriceStr = trim(row, 4);
                    String unitOfMeasure = trim(row, 5);
                    String barcode = trim(row, 6);

                    if (sku.isEmpty()) {
                        errors.add(new RowError(rowNum, "sku is required"));
                        continue;
                    }
                    if (name.isEmpty()) {
                        errors.add(new RowError(rowNum, "name is required"));
                        continue;
                    }
                    if (unitPriceStr.isEmpty()) {
                        errors.add(new RowError(rowNum, "unitPrice is required"));
                        continue;
                    }
                    if (productRepository.existsBySkuAndDistributorId(sku, distributorId)) {
                        errors.add(new RowError(rowNum, "Product with SKU " + sku + " already exists"));
                        continue;
                    }

                    BigDecimal unitPrice;
                    try {
                        unitPrice = new BigDecimal(unitPriceStr);
                    } catch (NumberFormatException e) {
                        errors.add(new RowError(rowNum, "Invalid unitPrice: " + unitPriceStr));
                        continue;
                    }

                    BigDecimal costPrice = null;
                    if (!costPriceStr.isEmpty()) {
                        try {
                            costPrice = new BigDecimal(costPriceStr);
                        } catch (NumberFormatException e) {
                            errors.add(new RowError(rowNum, "Invalid costPrice: " + costPriceStr));
                            continue;
                        }
                    }

                    ProductCategory category = null;
                    if (!categoryName.isEmpty()) {
                        category = productCategoryRepository
                                .findByNameAndDistributorId(categoryName, distributorId)
                                .orElse(null);
                    }

                    Product product = Product.builder()
                            .distributor(distributor)
                            .sku(sku)
                            .name(name)
                            .unitPrice(unitPrice)
                            .costPrice(costPrice)
                            .unitOfMeasure(unitOfMeasure.isEmpty() ? "PIECE" : unitOfMeasure)
                            .barcode(barcode.isEmpty() ? null : barcode)
                            .category(category)
                            .approvalStatus("APPROVED")
                            .build();

                    productRepository.save(product);
                    imported++;
                } catch (Exception e) {
                    errors.add(new RowError(rowNum, e.getMessage()));
                }
            }
        } catch (Exception e) {
            log.error("Error reading CSV file for product import: {}", e.getMessage());
            errors.add(new RowError(0, "Failed to read file: " + e.getMessage()));
        }

        return new ImportResult(imported, errors.size(), errors);
    }

    private String trim(String[] row, int index) {
        if (index >= row.length) return "";
        return row[index] == null ? "" : row[index].trim();
    }

    private String generateCode(String prefix, long count) {
        return prefix + String.format("%06d", count + 1);
    }
}
