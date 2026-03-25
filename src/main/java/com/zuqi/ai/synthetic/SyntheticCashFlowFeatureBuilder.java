package com.zuqi.ai.synthetic;

import com.zuqi.ai.feature.CashFlowFeatures;
import com.zuqi.ai.synthetic.dto.SyntheticCashFlowSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds CashFlowFeatures from SyntheticCashFlowSnapshot records.
 *
 * Mirrors the logic of CashFlowFeatureServiceImpl so training and inference use
 * identical feature representations.
 */
@Component
@Slf4j
public class SyntheticCashFlowFeatureBuilder {

    /**
     * Convert a snapshot to its CashFlowFeatures representation.
     * The {@code netCashFlow} from the snapshot is the regression target,
     * not a feature.
     */
    public CashFlowFeatures computeFeatures(SyntheticCashFlowSnapshot snapshot) {
        return new CashFlowFeatures(
                snapshot.distributorRef(),
                snapshot.date(),
                snapshot.pendingOrdersValue(),
                snapshot.avgDailyCollections7d(),
                snapshot.avgDailyCollections30d(),
                snapshot.collectionTrend(),
                snapshot.overdueReceivablesTotal(),
                snapshot.paymentDueNext7d(),
                snapshot.pendingPurchaseOrdersValue(),
                snapshot.avgDailyExpenses30d(),
                snapshot.upcomingSupplierPayments(),
                snapshot.date().getDayOfWeek().getValue(),  // 1=Mon … 7=Sun
                snapshot.date().getDayOfMonth(),
                snapshot.isPaydayWeek() ? 1.0 : 0.0,
                snapshot.isMonthEnd() ? 1.0 : 0.0,
                snapshot.netCashFlow7dAgo(),
                snapshot.netCashFlow30dAgo()
        );
    }

    /**
     * Convert all snapshots to labelled (features, target) pairs.
     * Target is the actual net cash flow from the snapshot.
     */
    public List<LabelledCashFlowExample> buildLabelledExamples(
            List<SyntheticCashFlowSnapshot> snapshots) {

        List<LabelledCashFlowExample> examples = snapshots.stream()
                .map(s -> new LabelledCashFlowExample(computeFeatures(s), s.netCashFlow()))
                .toList();

        log.info("Built {} cash flow labelled examples", examples.size());
        return examples;
    }

    public record LabelledCashFlowExample(CashFlowFeatures features, double netCashFlow) {}
}
