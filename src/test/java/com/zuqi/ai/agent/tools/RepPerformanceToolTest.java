package com.zuqi.ai.agent.tools;

import com.zuqi.domain.user.Role;
import com.zuqi.domain.user.User;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for {@link RepPerformanceTool}.
 */
@ExtendWith(MockitoExtension.class)
class RepPerformanceToolTest {

    @Mock private UserRepository  userRepository;
    @Mock private OrderRepository orderRepository;

    @InjectMocks
    private RepPerformanceTool tool;

    // ── happy paths ───────────────────────────────────────────────────────

    @Test
    void getRepPerformance_whenNoSalesReps_returnsZeroTotalWithMessage() {
        UUID distributorId = UUID.randomUUID();

        // Active users but none have SALES_REP role
        User manager = mockUser("MANAGER");
        when(userRepository.findByDistributorIdAndActiveTrue(distributorId))
                .thenReturn(List.of(manager));

        String result = tool.getRepPerformance(distributorId.toString());

        assertThat(result).contains("\"tool\": \"RepPerformance\"");
        assertThat(result).contains("\"totalSalesReps\": 0");
        assertThat(result).contains("\"message\"");
        verifyNoInteractions(orderRepository);
    }

    @Test
    void getRepPerformance_withSalesReps_returnsTopAndBottomPerformers() {
        UUID distributorId = UUID.randomUUID();

        User rep1 = mockUser("SALES_REP");
        User rep2 = mockUser("SALES_REP");
        when(rep1.getFirstName()).thenReturn("Alice");
        when(rep1.getLastName()).thenReturn("K");
        when(rep2.getFirstName()).thenReturn("Bob");
        when(rep2.getLastName()).thenReturn("M");

        when(userRepository.findByDistributorIdAndActiveTrue(distributorId))
                .thenReturn(List.of(rep1, rep2));

        // rep1 has 20 orders, rep2 has 5 orders
        when(orderRepository.findBySalesRepId(eq(rep1.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 1), 20));
        when(orderRepository.findBySalesRepId(eq(rep2.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 1), 5));

        String result = tool.getRepPerformance(distributorId.toString());

        assertThat(result).contains("\"totalSalesReps\": 2");
        assertThat(result).contains("\"topPerformers\"");
        assertThat(result).contains("\"bottomPerformers\"");
    }

    @Test
    void getRepPerformance_withEmptyUserList_returnsZero() {
        UUID distributorId = UUID.randomUUID();
        when(userRepository.findByDistributorIdAndActiveTrue(distributorId)).thenReturn(List.of());

        String result = tool.getRepPerformance(distributorId.toString());

        assertThat(result).contains("\"totalSalesReps\": 0");
    }

    // ── error paths ───────────────────────────────────────────────────────

    @Test
    void getRepPerformance_whenInvalidUuid_returnsErrorJson() {
        String result = tool.getRepPerformance("bad");

        assertThat(result).contains("\"error\"");
        verifyNoInteractions(userRepository, orderRepository);
    }

    @Test
    void getRepPerformance_whenRepositoryThrows_returnsErrorJson() {
        when(userRepository.findByDistributorIdAndActiveTrue(any()))
                .thenThrow(new RuntimeException("Query failed"));

        String result = tool.getRepPerformance(UUID.randomUUID().toString());

        assertThat(result).contains("\"error\"");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private User mockUser(String roleName) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(UUID.randomUUID());

        Role role = mock(Role.class);
        when(role.getName()).thenReturn(roleName);
        when(user.getRoles()).thenReturn(Set.of(role));

        return user;
    }
}
