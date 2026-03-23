package com.zuqi.ai.agent.tools;

import com.zuqi.repository.CustomerSegmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CustomerSegmentTool}.
 *
 * Covers: happy path with segment counts, all-zero segments,
 * invalid UUID, and repository exception.
 */
@ExtendWith(MockitoExtension.class)
class CustomerSegmentToolTest {

    @Mock private CustomerSegmentRepository customerSegmentRepository;

    @InjectMocks
    private CustomerSegmentTool tool;

    // ── happy paths ───────────────────────────────────────────────────────

    @Test
    void happyPath_returnsJsonWithCorrectToolName() {
        UUID distId = UUID.randomUUID();

        when(customerSegmentRepository.countByDistributorIdAndSegmentLabel(distId, "HIGH_VALUE_GROWING"))
                .thenReturn(42L);
        when(customerSegmentRepository.countByDistributorIdAndSegmentLabel(distId, "STABLE_MID_TIER"))
                .thenReturn(85L);
        when(customerSegmentRepository.countByDistributorIdAndSegmentLabel(distId, "AT_RISK_DECLINING"))
                .thenReturn(18L);
        when(customerSegmentRepository.countByDistributorIdAndSegmentLabel(distId, "NEW_LOW_ACTIVITY"))
                .thenReturn(31L);
        when(customerSegmentRepository.countByDistributorIdAndSegmentLabel(distId, "HIGH_VALUE_AT_RISK"))
                .thenReturn(9L);

        String result = tool.getCustomerSegments(distId.toString());

        assertThat(result).contains("\"tool\": \"CustomerSegments\"");
        assertThat(result).contains("\"HIGH_VALUE_GROWING\": 42");
        assertThat(result).contains("\"STABLE_MID_TIER\": 85");
        assertThat(result).contains("\"AT_RISK_DECLINING\": 18");
        assertThat(result).contains("\"NEW_LOW_ACTIVITY\": 31");
        assertThat(result).contains("\"HIGH_VALUE_AT_RISK\": 9");
        assertThat(result).contains("\"totalSegmented\": 185");
    }

    @Test
    void emptyList_returnsValidJsonWithZeroCounts() {
        UUID distId = UUID.randomUUID();

        when(customerSegmentRepository.countByDistributorIdAndSegmentLabel(any(UUID.class), anyString()))
                .thenReturn(0L);

        String result = tool.getCustomerSegments(distId.toString());

        assertThat(result).contains("\"tool\": \"CustomerSegments\"");
        assertThat(result).contains("\"HIGH_VALUE_GROWING\": 0");
        assertThat(result).contains("\"STABLE_MID_TIER\": 0");
        assertThat(result).contains("\"AT_RISK_DECLINING\": 0");
        assertThat(result).contains("\"NEW_LOW_ACTIVITY\": 0");
        assertThat(result).contains("\"HIGH_VALUE_AT_RISK\": 0");
        assertThat(result).contains("\"totalSegmented\": 0");
    }

    // ── error paths ───────────────────────────────────────────────────────

    @Test
    void invalidUuid_returnsErrorJson() {
        String result = tool.getCustomerSegments("not-uuid");

        assertThat(result).contains("\"error\"");
        verifyNoInteractions(customerSegmentRepository);
    }

    @Test
    void repositoryThrows_returnsErrorJson() {
        when(customerSegmentRepository.countByDistributorIdAndSegmentLabel(any(UUID.class), anyString()))
                .thenThrow(new RuntimeException("Query timeout"));

        String result = tool.getCustomerSegments(UUID.randomUUID().toString());

        assertThat(result).contains("\"error\"");
    }
}
