package com.zuqi.ai.cashflow;

import com.zuqi.ai.feature.CashFlowFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tribuo.Example;
import org.tribuo.MutableDataset;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.RegressionFactory;
import org.tribuo.regression.Regressor;

import java.util.List;

/**
 * Converts CashFlowFeatures into Tribuo Example<Regressor> for XGBoost training/inference.
 *
 * Features (16):
 * 1.  pending_orders_value
 * 2.  avg_daily_collections_7d
 * 3.  avg_daily_collections_30d
 * 4.  collection_trend
 * 5.  overdue_receivables_total
 * 6.  payment_due_next_7d
 * 7.  pending_purchase_orders_value
 * 8.  avg_daily_expenses_30d
 * 9.  upcoming_supplier_payments
 * 10. day_of_week
 * 11. day_of_month
 * 12. is_payday_week
 * 13. is_month_end
 * 14. net_cash_flow_7d_ago
 * 15. net_cash_flow_30d_ago
 * 16. rolling_avg_net_flow_7d   (avg7dCollections - avg7dExpenses proxy)
 *
 * Target: net_cash_flow (KES, can be negative)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CashFlowFeatureBuilder {

    static final int FEATURE_COUNT = 16;
    private static final RegressionFactory REGRESSION_FACTORY = new RegressionFactory();

    private static final String[] FEATURE_NAMES = {
            "pending_orders_value",
            "avg_daily_collections_7d",
            "avg_daily_collections_30d",
            "collection_trend",
            "overdue_receivables_total",
            "payment_due_next_7d",
            "pending_purchase_orders_value",
            "avg_daily_expenses_30d",
            "upcoming_supplier_payments",
            "day_of_week",
            "day_of_month",
            "is_payday_week",
            "is_month_end",
            "net_cash_flow_7d_ago",
            "net_cash_flow_30d_ago",
            "rolling_avg_net_flow_7d"
    };

    public int getFeatureCount() {
        return FEATURE_COUNT;
    }

    /**
     * Build inference example. Net cash flow target is set to 0 as placeholder.
     */
    public Example<Regressor> buildExample(CashFlowFeatures features) {
        return buildLabelledExample(features, 0.0);
    }

    /**
     * Build a labelled training example.
     *
     * @param features     computed feature record
     * @param netCashFlow  actual net cash flow (KES) — the regression target
     */
    public Example<Regressor> buildLabelledExample(CashFlowFeatures features, double netCashFlow) {
        // Rolling avg net flow: proxy as avg collections 7d minus avg expenses 30d
        double rollingAvgNetFlow7d = features.avgDailyCollections7d() - features.avgDailyExpenses30d();

        double[] values = {
                cap(features.pendingOrdersValue()),
                cap(features.avgDailyCollections7d()),
                cap(features.avgDailyCollections30d()),
                features.collectionTrend(),                              // can be negative
                cap(features.overdueReceivablesTotal()),
                cap(features.paymentDueNext7d()),
                cap(features.pendingPurchaseOrdersValue()),
                cap(features.avgDailyExpenses30d()),
                cap(features.upcomingSupplierPayments()),
                features.dayOfWeek(),
                features.dayOfMonth(),
                features.isPaydayWeek(),
                features.isMonthEnd(),
                features.netCashFlow7dAgo(),                             // can be negative
                features.netCashFlow30dAgo(),                            // can be negative
                rollingAvgNetFlow7d
        };

        Regressor target = new Regressor("net_cash_flow", netCashFlow);
        return new ArrayExample<>(target, FEATURE_NAMES, values);
    }

    /**
     * Build Tribuo dataset from labelled examples.
     */
    public MutableDataset<Regressor> buildDataset(
            List<com.zuqi.ai.synthetic.SyntheticCashFlowFeatureBuilder.LabelledCashFlowExample> examples) {

        MutableDataset<Regressor> dataset = new MutableDataset<>(
                new SimpleDataSourceProvenance("CashFlowFeatureBuilder", REGRESSION_FACTORY),
                REGRESSION_FACTORY);

        for (var le : examples) {
            dataset.add(buildLabelledExample(le.features(), le.netCashFlow()));
        }
        return dataset;
    }

    /** Cap large KES values to reduce XGBoost sensitivity to outliers. */
    private double cap(double value) {
        return Math.min(10_000_000.0, Math.max(0.0, value));
    }
}
