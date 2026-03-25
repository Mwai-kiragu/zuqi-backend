package com.zuqi.ai.recon;

import com.zuqi.ai.feature.ReconFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tribuo.Example;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;

import java.util.List;

/**
 * Converts ReconFeatures into Tribuo Example<Label> for XGBoost training/inference.
 *
 * Features (8):
 * 1. amount_diff_pct
 * 2. amount_exact_match
 * 3. date_diff_days
 * 4. reference_exact_match
 * 5. reference_similarity
 * 6. description_similarity
 * 7. same_merchant
 * 8. payment_method_match
 *
 * Target: MATCH or NO_MATCH
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReconFeatureBuilder {

    static final int FEATURE_COUNT = 8;
    static final String LABEL_MATCH = "MATCH";
    static final String LABEL_NO_MATCH = "NO_MATCH";

    private static final LabelFactory LABEL_FACTORY = new LabelFactory();
    private static final String[] FEATURE_NAMES = {
            "amount_diff_pct",
            "amount_exact_match",
            "date_diff_days",
            "reference_exact_match",
            "reference_similarity",
            "description_similarity",
            "same_merchant",
            "payment_method_match"
    };

    public int getFeatureCount() {
        return FEATURE_COUNT;
    }

    /**
     * Build inference example. Label is set to NO_MATCH as a placeholder.
     */
    public Example<Label> buildExample(ReconFeatures features) {
        return buildLabelledExample(features, LABEL_NO_MATCH);
    }

    /**
     * Build a labelled training example.
     *
     * @param features computed feature record
     * @param label    "MATCH" or "NO_MATCH"
     */
    public Example<Label> buildLabelledExample(ReconFeatures features, String label) {
        double[] values = {
                clip(features.amountDiffPct(), 0.0, 5.0),
                features.amountExactMatch(),
                Math.min(features.dateDiffDays(), 30.0),
                features.referenceExactMatch(),
                clip(features.referenceSimilarity(), 0.0, 1.0),
                clip(features.descriptionSimilarity(), 0.0, 1.0),
                features.sameMerchant(),
                features.paymentMethodMatch()
        };

        Label target = new Label(label);
        return new ArrayExample<>(target, FEATURE_NAMES, values);
    }

    /**
     * Build a Tribuo dataset from labelled examples.
     */
    public MutableDataset<Label> buildDataset(
            List<com.zuqi.ai.synthetic.SyntheticReconFeatureBuilder.LabelledReconExample> examples) {

        MutableDataset<Label> dataset = new MutableDataset<>(
                new SimpleDataSourceProvenance("ReconFeatureBuilder", LABEL_FACTORY),
                LABEL_FACTORY);

        for (var le : examples) {
            dataset.add(buildLabelledExample(le.features(), le.label()));
        }
        return dataset;
    }

    private double clip(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }
}
