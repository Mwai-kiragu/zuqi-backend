package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.CustomerHealthScore;
import com.zuqi.domain.customer.Customer;
import com.zuqi.repository.CustomerHealthScoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CustomerHealthTool}.
 *
 * Covers: happy path with tier distribution and priority customers,
 * all-empty tiers, invalid UUID, and repository exception.
 */
@ExtendWith(MockitoExtension.class)
class CustomerHealthToolTest {

    @Mock private CustomerHealthScoreRepository customerHealthScoreRepository;

    @InjectMocks
    private CustomerHealthTool tool;

    // ── happy paths ───────────────────────────────────────────────────────

    @Test
    void happyPath_returnsJsonWithCorrectToolName() {
        UUID distId = UUID.randomUUID();

        CustomerHealthScore thriving1 = mockHealthScore("Mama Pima Duka", "THRIVING", 92.0);
        CustomerHealthScore thriving2 = mockHealthScore("Baraka Stores",  "THRIVING", 88.5);
        CustomerHealthScore healthy1  = mockHealthScore("Jua Kali Shop",  "HEALTHY",  74.0);
        CustomerHealthScore atRisk1   = mockHealthScore("Grace Mini Mart","AT_RISK",  38.0);
        CustomerHealthScore critical1 = mockHealthScore("Wanjiku Kiosk",  "CRITICAL", 15.0);

        when(customerHealthScoreRepository.findByDistributorIdAndHealthTier(distId, "THRIVING"))
                .thenReturn(List.of(thriving1, thriving2));
        when(customerHealthScoreRepository.findByDistributorIdAndHealthTier(distId, "HEALTHY"))
                .thenReturn(List.of(healthy1));
        when(customerHealthScoreRepository.findByDistributorIdAndHealthTier(distId, "NEEDS_ATTENTION"))
                .thenReturn(List.of());
        when(customerHealthScoreRepository.findByDistributorIdAndHealthTier(distId, "AT_RISK"))
                .thenReturn(List.of(atRisk1));
        when(customerHealthScoreRepository.findByDistributorIdAndHealthTier(distId, "CRITICAL"))
                .thenReturn(List.of(critical1));

        String result = tool.getCustomerHealth(distId.toString());

        assertThat(result).contains("\"tool\": \"CustomerHealth\"");
        assertThat(result).contains("\"THRIVING\": 2");
        assertThat(result).contains("\"HEALTHY\": 1");
        assertThat(result).contains("\"AT_RISK\": 1");
        assertThat(result).contains("\"CRITICAL\": 1");
        assertThat(result).contains("\"priorityCustomers\"");
        assertThat(result).contains("Wanjiku Kiosk");
        assertThat(result).contains("Grace Mini Mart");
    }

    @Test
    void emptyList_returnsValidJsonWithZeroCounts() {
        UUID distId = UUID.randomUUID();

        when(customerHealthScoreRepository.findByDistributorIdAndHealthTier(any(UUID.class), anyString()))
                .thenReturn(List.of());

        String result = tool.getCustomerHealth(distId.toString());

        assertThat(result).contains("\"tool\": \"CustomerHealth\"");
        assertThat(result).contains("\"THRIVING\": 0");
        assertThat(result).contains("\"HEALTHY\": 0");
        assertThat(result).contains("\"AT_RISK\": 0");
        assertThat(result).contains("\"CRITICAL\": 0");
        assertThat(result).contains("\"priorityCustomers\": []");
    }

    // ── error paths ───────────────────────────────────────────────────────

    @Test
    void invalidUuid_returnsErrorJson() {
        String result = tool.getCustomerHealth("not-uuid");

        assertThat(result).contains("\"error\"");
        verifyNoInteractions(customerHealthScoreRepository);
    }

    @Test
    void repositoryThrows_returnsErrorJson() {
        when(customerHealthScoreRepository.findByDistributorIdAndHealthTier(any(UUID.class), anyString()))
                .thenThrow(new RuntimeException("Unexpected DB error"));

        String result = tool.getCustomerHealth(UUID.randomUUID().toString());

        assertThat(result).contains("\"error\"");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private CustomerHealthScore mockHealthScore(String businessName, String tier, double score) {
        Customer customer = mock(Customer.class);
        lenient().when(customer.getBusinessName()).thenReturn(businessName);

        CustomerHealthScore h = mock(CustomerHealthScore.class);
        lenient().when(h.getCustomer()).thenReturn(customer);
        lenient().when(h.getHealthTier()).thenReturn(tier);
        lenient().when(h.getHealthScore()).thenReturn(score);
        return h;
    }
}
