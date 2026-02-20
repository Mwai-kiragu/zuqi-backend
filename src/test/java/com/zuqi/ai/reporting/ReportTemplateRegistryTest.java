package com.zuqi.ai.reporting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for {@link ReportTemplateRegistry}.
 *
 * Pure JUnit 5 — no Spring context required. The registry is constructed
 * directly with {@code new ReportTemplateRegistry()}.
 *
 * Blueprint reference: implementation_plan.md Phase 6, Task 6.3
 */
class ReportTemplateRegistryTest {

    private ReportTemplateRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ReportTemplateRegistry();
    }

    // ── Principal lookup ─────────────────────────────────────────────────────

    @Test
    void getTemplate_unilever_returnsUnileverTemplate() {
        ReportTemplateRegistry.ReportTemplate template = registry.getTemplate("UNILEVER");

        assertThat(template).isNotNull();
        assertThat(template.principal()).isEqualTo("Unilever Kenya");
    }

    @Test
    void getTemplate_caseInsensitive_works() {
        ReportTemplateRegistry.ReportTemplate upper = registry.getTemplate("UNILEVER");
        ReportTemplateRegistry.ReportTemplate lower = registry.getTemplate("unilever");
        ReportTemplateRegistry.ReportTemplate mixed = registry.getTemplate("Unilever");

        assertThat(lower.principal()).isEqualTo(upper.principal());
        assertThat(mixed.principal()).isEqualTo(upper.principal());
    }

    @Test
    void getTemplate_pandag_returnsTemplate() {
        ReportTemplateRegistry.ReportTemplate template = registry.getTemplate("P_AND_G");

        assertThat(template).isNotNull();
        assertThat(template.principal()).isNotBlank();
        assertThat(template.sections()).isNotBlank();
        assertThat(template.tone()).isNotBlank();
        assertThat(template.requiredMetrics()).isNotBlank();
    }

    @Test
    void getTemplate_eabl_returnsTemplate() {
        ReportTemplateRegistry.ReportTemplate template = registry.getTemplate("EABL");

        assertThat(template).isNotNull();
        assertThat(template.principal()).isEqualTo("East African Breweries Limited");
    }

    // ── Default fallback ─────────────────────────────────────────────────────

    @Test
    void getTemplate_unknown_returnsDefault() {
        ReportTemplateRegistry.ReportTemplate template = registry.getTemplate("NONEXISTENT");

        assertThat(template).isNotNull();
        // The DEFAULT template has this principal name
        assertThat(template.principal()).isEqualTo("Principal Manufacturer");
    }

    @Test
    void getTemplate_null_returnsDefault() {
        assertThatNoException().isThrownBy(() -> {
            ReportTemplateRegistry.ReportTemplate template = registry.getTemplate(null);
            assertThat(template).isNotNull();
            assertThat(template.principal()).isEqualTo("Principal Manufacturer");
        });
    }

    @Test
    void getTemplate_blank_returnsDefault() {
        ReportTemplateRegistry.ReportTemplate template = registry.getTemplate("");

        assertThat(template).isNotNull();
        assertThat(template.principal()).isEqualTo("Principal Manufacturer");
    }

    // ── getSupportedPrincipals ───────────────────────────────────────────────

    @Test
    void getSupportedPrincipals_doesNotContainDefault() {
        List<String> principals = registry.getSupportedPrincipals();

        assertThat(principals).doesNotContain("DEFAULT");
    }

    @Test
    void getSupportedPrincipals_containsExpectedPrincipals() {
        List<String> principals = registry.getSupportedPrincipals();

        assertThat(principals).contains("UNILEVER", "EABL", "P_AND_G");
    }

    @Test
    void getSupportedPrincipals_isSorted() {
        List<String> principals = registry.getSupportedPrincipals();

        // assertThat isSortedAccordingTo with natural order is equivalent to checking
        // the list equals its sorted self
        assertThat(principals).isSorted();
    }

    // ── Template content integrity ───────────────────────────────────────────

    @Test
    void template_hasNonBlankSections() {
        ReportTemplateRegistry.ReportTemplate template = registry.getTemplate("UNILEVER");

        assertThat(template.sections()).isNotBlank();
        assertThat(template.tone()).isNotBlank();
        assertThat(template.requiredMetrics()).isNotBlank();
    }

    @Test
    void template_hasNonBlankPrincipal() {
        // Every supported principal plus the DEFAULT must have a non-blank principal name
        List<String> allKeys = registry.getSupportedPrincipals();

        for (String key : allKeys) {
            ReportTemplateRegistry.ReportTemplate template = registry.getTemplate(key);
            assertThat(template.principal())
                    .as("Principal name must not be blank for key: %s", key)
                    .isNotBlank();
        }

        // Also verify DEFAULT directly
        ReportTemplateRegistry.ReportTemplate defaultTemplate = registry.getTemplate(null);
        assertThat(defaultTemplate.principal()).isNotBlank();
    }
}
