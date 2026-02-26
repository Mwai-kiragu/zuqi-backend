package com.zuqi.ai.event;

import com.zuqi.domain.ai.DataPhase;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Published by {@link com.zuqi.ai.synthetic.DataPhaseTracker} whenever a model's data
 * maturity phase advances (SYNTHETIC → HYBRID or HYBRID → REAL).
 *
 * <p>Transitions are monotonic — a phase never goes backwards. Listeners can use
 * this event to:
 * <ul>
 *   <li>Adjust confidence modifiers applied to the model's predictions</li>
 *   <li>Trigger a fresh training run under the new blending strategy</li>
 *   <li>Update dashboards or audit logs</li>
 * </ul>
 *
 * @see com.zuqi.ai.synthetic.DataPhaseTracker
 */
@Getter
public class DataPhaseTransitionEvent extends ApplicationEvent {

    /** Model whose phase changed (e.g. {@code "credit_classifier"}). */
    private final String    modelName;

    /** Distributor scope, or {@code null} for a global/shared model. */
    private final UUID      distributorId;

    private final DataPhase fromPhase;
    private final DataPhase toPhase;

    /** Real-data ratio at the time of transition (0.0 – 1.0). */
    private final double    realDataRatio;

    /** Absolute count of real training examples at the time of transition. */
    private final int       realDataCount;

    public DataPhaseTransitionEvent(Object source,
                                    String modelName,
                                    UUID distributorId,
                                    DataPhase fromPhase,
                                    DataPhase toPhase,
                                    double realDataRatio,
                                    int realDataCount) {
        super(source);
        this.modelName     = modelName;
        this.distributorId = distributorId;
        this.fromPhase     = fromPhase;
        this.toPhase       = toPhase;
        this.realDataRatio = realDataRatio;
        this.realDataCount = realDataCount;
    }
}
