package com.claimpilot.eval;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.claimpilot.samples.SampleDocuments;

/**
 * Measures accuracy with the real models (Ollama must be running with qwen3:8b and bge-m3), on the
 * fictional sample documents: answer status, cited page, expected content, and extracted facts.
 * Not part of the normal build; run with {@code ./mvnw test -Peval}. Writes target/eval-report.md.
 */
@Tag("eval")
@SpringBootTest(properties = {"claimpilot.cache.model-requests-per-minute=100000",
        "claimpilot.cache.login-attempts-per-minute=100000"})
@AutoConfigureMockMvc
class AccuracyEvaluationTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @ServiceConnection
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    static {
        postgres.start();
        mongo.start();
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) throws Exception {
        Path uploads = Files.createTempDirectory("claimpilot-eval");
        registry.add("claimpilot.storage.local-root", uploads::toString);
    }

    @Autowired
    MockMvc mvc;

    @Test
    void run() throws Exception {
        JsonNode cases;
        try (InputStream in = getClass().getResourceAsStream("/eval/cases.json")) {
            cases = JsonMapper.builder().build().readTree(in);
        }
        String token = JsonPath.read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"fiona\",\"password\":\"demo1234\"}"))
                .andReturn().getResponse().getContentAsString(), "$.token");

        Map<String, String> ids = new HashMap<>();
        ids.put("cedarview", upload(token, "policies", SampleDocuments.SPOUSE_POLICY, SampleDocuments.spousePolicy()));
        ids.put("harbourline", upload(token, "policies", SampleDocuments.OWN_POLICY, SampleDocuments.ownPolicyFrench()));
        ids.put("receipt", upload(token, "receipts", SampleDocuments.RECEIPT_PDF, SampleDocuments.receiptPdf()));

        List<String> rows = new ArrayList<>();
        int statusOk = 0;
        int pageOk = 0;
        int pageCases = 0;
        int contentOk = 0;
        long totalMs = 0;
        for (JsonNode c : cases.get("questions")) {
            String policyId = ids.get(c.get("policy").asString());
            long start = System.nanoTime();
            String answer = mvc.perform(auth(token, post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                            .content(JsonMapper.builder().build().writeValueAsString(Map.of(
                                    "question", c.get("question").asString(), "policyId", policyId)))))
                    .andReturn().getResponse().getContentAsString();
            long ms = (System.nanoTime() - start) / 1_000_000;
            totalMs += ms;

            String status = JsonPath.read(answer, "$.status");
            String text = JsonPath.read(answer, "$.answer");
            List<Integer> pages = JsonPath.read(answer, "$.citations[*].page");
            boolean statusMatch = status.equals(c.get("status").asString());
            boolean pageMatch = true;
            if (!c.get("page").isNull()) {
                pageCases++;
                pageMatch = pages.contains(c.get("page").asInt());
                pageOk += pageMatch ? 1 : 0;
            }
            boolean contentMatch = true;
            for (JsonNode word : c.get("mustContain")) {
                contentMatch &= text.toLowerCase(Locale.ROOT).contains(word.asString().toLowerCase(Locale.ROOT));
            }
            statusOk += statusMatch ? 1 : 0;
            contentOk += contentMatch ? 1 : 0;
            rows.add("| " + c.get("question").asString() + " | " + c.get("status").asString() + " | " + status
                    + (statusMatch ? "" : " ✗") + " | " + pages + (pageMatch ? "" : " ✗") + " | "
                    + (contentMatch ? "yes" : "no ✗") + " | " + ms + " ms |");
        }

        int factOk = 0;
        List<String> factRows = new ArrayList<>();
        Map<String, String> bodies = new HashMap<>();
        bodies.put("cedarview", body(token, "/api/policies/" + ids.get("cedarview")));
        bodies.put("harbourline", body(token, "/api/policies/" + ids.get("harbourline")));
        bodies.put("receipt", body(token, "/api/receipts/" + ids.get("receipt")));
        for (JsonNode f : cases.get("facts")) {
            String doc = bodies.get(f.get("document").asString());
            List<String> values = JsonPath.read(doc, "$.facts[?(@.key == '" + f.get("key").asString() + "')].value");
            List<Boolean> verified = JsonPath.read(doc, "$.facts[?(@.key == '" + f.get("key").asString() + "')].verified");
            boolean ok = values.contains(f.get("value").asString());
            factOk += ok ? 1 : 0;
            factRows.add("| " + f.get("document").asString() + " | " + f.get("key").asString() + " | "
                    + f.get("value").asString() + " | " + values + (ok ? "" : " ✗") + " | " + verified + " |");
        }

        int questions = cases.get("questions").size();
        int facts = cases.get("facts").size();
        StringBuilder report = new StringBuilder("# ClaimPilot accuracy report\n\n")
                .append("| Metric | Result |\n|---|---|\n")
                .append("| Answer status (answered / unclear / not in policy) | ").append(pct(statusOk, questions)).append(" |\n")
                .append("| Cited the expected page | ").append(pct(pageOk, pageCases)).append(" |\n")
                .append("| Answer contains the expected facts | ").append(pct(contentOk, questions)).append(" |\n")
                .append("| Key facts extracted correctly | ").append(pct(factOk, facts)).append(" |\n")
                .append("| Average answer time | ").append(totalMs / Math.max(questions, 1)).append(" ms |\n\n")
                .append("## Questions\n\n| Question | Expected | Got | Cited pages | Content | Time |\n|---|---|---|---|---|---|\n");
        rows.forEach(r -> report.append(r).append('\n'));
        report.append("\n## Facts\n\n| Document | Fact | Expected | Got | Verified |\n|---|---|---|---|---|\n");
        factRows.forEach(r -> report.append(r).append('\n'));
        Path out = Path.of("target", "eval-report.md");
        Files.writeString(out, report);
        System.out.println(report);
        System.out.println("Report written to " + out.toAbsolutePath());
    }

    private static String pct(int ok, int total) {
        return total == 0 ? "n/a" : ok + " / " + total + " (" + Math.round(100.0 * ok / total) + "%)";
    }

    private String upload(String token, String collection, String name, byte[] content) throws Exception {
        String id = JsonPath.read(mvc.perform(auth(token, multipart("/api/" + collection)
                        .file(new MockMultipartFile("file", name, "application/octet-stream", content))))
                .andReturn().getResponse().getContentAsString(), "$.id");
        long deadline = System.nanoTime() + Duration.ofMinutes(5).toNanos();
        while (System.nanoTime() < deadline) {
            String status = JsonPath.read(body(token, "/api/" + collection + "/" + id), "$.status");
            if ("READY".equals(status) || "FAILED".equals(status)) {
                return id;
            }
            Thread.sleep(500);
        }
        throw new AssertionError(name + " was not processed in time");
    }

    private String body(String token, String url) throws Exception {
        return mvc.perform(auth(token, get(url))).andReturn().getResponse().getContentAsString();
    }

    private static <B extends org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<B>> B auth(
            String token, B request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
