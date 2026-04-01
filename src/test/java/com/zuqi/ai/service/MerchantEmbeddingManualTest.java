package com.zuqi.ai.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual integration test verifying the pgvector similarity search pipeline.
 *
 * Tests the full flow:
 *   RBS AI text-embedding-ada-002 → float vector → pgvector INSERT → cosine similarity query
 *
 * HOW TO RUN:
 *   ./mvnw test -Dtest=MerchantEmbeddingManualTest -Pintegration
 *   OR remove @Disabled and run: ./mvnw test -Dtest=MerchantEmbeddingManualTest
 *
 * REQUIRES:
 *   - RBS AI reachable at https://rbsai.rbrc.io with text-embedding-ada-002
 *   - PostgreSQL with pgvector at sifadevdb.cpv9gzf8h14i.eu-west-1.rds.amazonaws.com
 *   - A distributor_id that exists in the distributors table (see DISTRIBUTOR_ID below)
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.3
 */
@Disabled("Infrastructure test — requires live PostgreSQL + RBS AI. Remove @Disabled to run manually.")
class MerchantEmbeddingManualTest {

    // ── Infrastructure coordinates ────────────────────────────────────────────
    private static final String RBS_AI_URL      = "https://rbsai.rbrc.io/v1/embeddings";
    private static final String RBS_AI_MODEL    = "text-embedding-ada-002";

    private static final String DB_URL  = "jdbc:postgresql://sifadevdb.cpv9gzf8h14i.eu-west-1.rds.amazonaws.com:5432/zuqi_test";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "<n]a.0Zab[$Cqivg_[6y[dJm_wq]";

    // ── Test fixture data ────────────────────────────────────────────────────
    // Two merchant profiles that should be similar to each other
    private static final String RETAIL_PROFILE_A =
            "Merchant: Kamau General Store. Category: General Store. Location: Nairobi. " +
            "Orders: 48 total, 1.0/week. Average order: KES 5000. Trend: growing. " +
            "Payment: 92% on-time, 4 days average. Streak: 8. Credit: KES 50000, 40% utilization.";

    private static final String RETAIL_PROFILE_B =
            "Merchant: Wanjiku Retail Shop. Category: General Store. Location: Nairobi. " +
            "Orders: 52 total, 1.1/week. Average order: KES 4800. Trend: stable. " +
            "Payment: 89% on-time, 5 days average. Streak: 6. Credit: KES 48000, 45% utilization.";

    // Dissimilar profile — a wholesale distributor
    private static final String WHOLESALE_PROFILE_C =
            "Merchant: Rift Valley Wholesale Ltd. Category: Wholesale Distributor. Location: Nakuru. " +
            "Orders: 200 total, 4.0/week. Average order: KES 85000. Trend: stable. " +
            "Payment: 70% on-time, 15 days average. Streak: 2. Credit: KES 500000, 80% utilization.";

    // ── Test ─────────────────────────────────────────────────────────────────

    @Test
    void pgvectorSimilaritySearch_returnsCorrectlySortedResults() throws Exception {
        // ── Step 1: Generate embeddings via RBS AI ──────────────────────────
        System.out.println("Generating embeddings via RBS AI...");
        float[] vecA = embedText(RETAIL_PROFILE_A);
        float[] vecB = embedText(RETAIL_PROFILE_B);
        float[] vecC = embedText(WHOLESALE_PROFILE_C);

        assertThat(vecA).hasSizeGreaterThan(0);
        assertThat(vecB).hasSizeBetween(vecA.length, vecA.length); // same model → same dimension
        System.out.printf("Embedding dimension: %d%n", vecA.length);

        // ── Step 2: Compute cosine similarity manually for assertion baseline
        double simAB = cosineSimilarity(vecA, vecB);
        double simAC = cosineSimilarity(vecA, vecC);
        System.out.printf("Cosine similarity A↔B (retail/retail): %.4f%n", simAB);
        System.out.printf("Cosine similarity A↔C (retail/wholesale): %.4f%n", simAC);
        assertThat(simAB).isGreaterThan(simAC); // similar profiles should score higher

        // ── Step 3: Insert test rows into pgvector ──────────────────────────
        UUID distId  = UUID.randomUUID(); // isolated distributor for the test
        UUID mechIdA = UUID.randomUUID();
        UUID mechIdB = UUID.randomUUID();
        UUID mechIdC = UUID.randomUUID();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            insertTestEmbedding(conn, mechIdA, distId, "Kamau General Store",    toVectorString(vecA));
            insertTestEmbedding(conn, mechIdB, distId, "Wanjiku Retail Shop",    toVectorString(vecB));
            insertTestEmbedding(conn, mechIdC, distId, "Rift Valley Wholesale",  toVectorString(vecC));
            System.out.println("Inserted 3 test embeddings into pgvector.");

            // ── Step 4: Query similarity using the same SQL as the repository ──
            List<UUID> similar = findSimilarMerchants(conn, toVectorString(vecA), mechIdA, distId, 5);
            System.out.println("Similar merchants ranked: " + similar);

            assertThat(similar).isNotEmpty();
            // B (retail) should rank above C (wholesale) for query A (retail)
            int rankB = similar.indexOf(mechIdB);
            int rankC = similar.indexOf(mechIdC);
            assertThat(rankB).isGreaterThanOrEqualTo(0).as("Retail profile B must appear in results");
            assertThat(rankC).isGreaterThanOrEqualTo(0).as("Wholesale profile C must appear in results");
            assertThat(rankB).isLessThan(rankC)
                    .as("Retail B (rank %d) should rank closer to retail A than wholesale C (rank %d)", rankB, rankC);

            // ── Step 5: Clean up test rows ────────────────────────────────────
            cleanUpTestEmbeddings(conn, List.of(mechIdA, mechIdB, mechIdC));
            System.out.println("Test rows cleaned up.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private float[] embedText(String text) throws Exception {
        String body = String.format("{\"model\":\"%s\",\"input\":\"%s\"}", RBS_AI_MODEL,
                text.replace("\"", "\\\"").replace("\n", " "));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(RBS_AI_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        String resp = client.send(req, HttpResponse.BodyHandlers.ofString()).body();

        // Parse OpenAI-compatible response: "data":[{"embedding":[0.1,0.2,...]}]
        Matcher m = Pattern.compile("\"embedding\":\\s*\\[([^]]+)\\]").matcher(resp);
        assertThat(m.find()).as("No embedding found in response: " + resp.substring(0, Math.min(200, resp.length()))).isTrue();
        String[] parts = m.group(1).split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) vec[i] = Float.parseFloat(parts[i].trim());
        return vec;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, magA = 0, magB = 0;
        for (int i = 0; i < a.length; i++) {
            dot  += a[i] * b[i];
            magA += a[i] * a[i];
            magB += b[i] * b[i];
        }
        return dot / (Math.sqrt(magA) * Math.sqrt(magB));
    }

    private String toVectorString(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        return sb.append("]").toString();
    }

    private void insertTestEmbedding(Connection conn, UUID mechId, UUID distId,
                                      String name, String vector) throws SQLException {
        // Insert a minimal row — NULLs for FK columns not needed for this test
        String sql = """
                INSERT INTO ai_merchant_embeddings
                    (id, merchant_id, distributor_id, embedding, feature_summary, model_version, created_at, updated_at)
                VALUES (?, ?, ?, CAST(? AS vector), ?, 'text-embedding-ada-002', NOW(), NOW())
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, mechId);
            ps.setObject(3, distId);
            ps.setString(4, vector);
            ps.setString(5, name);
            ps.executeUpdate();
        }
    }

    private List<UUID> findSimilarMerchants(Connection conn, String queryVector,
                                              UUID excludeId, UUID distId, int limit) throws SQLException {
        String sql = """
                SELECT merchant_id FROM ai_merchant_embeddings
                WHERE distributor_id = ?
                  AND merchant_id != ?
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?
                """;
        List<UUID> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, distId);
            ps.setObject(2, excludeId);
            ps.setString(3, queryVector);
            ps.setInt(4, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) results.add((UUID) rs.getObject(1));
        }
        return results;
    }

    private void cleanUpTestEmbeddings(Connection conn, List<UUID> mechIds) throws SQLException {
        String sql = "DELETE FROM ai_merchant_embeddings WHERE merchant_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (UUID id : mechIds) {
                ps.setObject(1, id);
                ps.executeUpdate();
            }
        }
    }
}
