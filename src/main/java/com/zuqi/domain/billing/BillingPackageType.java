package com.zuqi.domain.billing;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum BillingPackageType {

    FREE_TRIAL {
        @Override
        public List<String> getIncludedModules() {
            return Arrays.asList("dashboard", "orders", "merchants", "customers", "products", "inventory");
        }
    },

    SILVER {
        @Override
        public List<String> getIncludedModules() {
            List<String> modules = new java.util.ArrayList<>(FREE_TRIAL.getIncludedModules());
            modules.addAll(Arrays.asList(
                "payments", "invoices", "suppliers", "procurement",
                "reports", "approvals", "pos", "branches",
                "stockTransfers", "stockTakes", "accounting",
                "expenses", "fundsTransfer", "warehouses", "paymentSetup"
            ));
            return modules;
        }
    },

    GOLD {
        @Override
        public List<String> getIncludedModules() {
            List<String> modules = new java.util.ArrayList<>(SILVER.getIncludedModules());
            modules.addAll(Arrays.asList("gl", "ai", "credit"));
            return modules;
        }
    },

    CUSTOM {
        @Override
        public List<String> getIncludedModules() {
            // Resolved from the custom_modules column at runtime
            return Collections.emptyList();
        }
    };

    public abstract List<String> getIncludedModules();
}
