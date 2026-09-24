package com.claimpilot.eval;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

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
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.claimpilot.samples.SampleDocuments;

/**
 * Measures accuracy with the real models (Ollama must be running with qwen3:8b and bge-m3): answer
 * status, cited page, expected content, and extracted facts.
 * <p>
 * Two kinds of case sets:
 * <ul>
 *   <li>the public set, {@code src/test/resources/eval/cases.json}, on the fictional sample documents;</li>
 *   <li>private sets on real policies: every {@code *.json} in {@code sample-docs/private/eval} (never
 *       committed), whose documents are files next to it. See docs/evaluation.md for the format.</li>
 * </ul>
 * Not part of the normal build; run with {@code ./mvnw test -Peval}. Writes target/eval-report.md.
 */
@Tag("eval")
@SpringBootTest(properties = {"claimpilot.cache.model-requests-per-minute=100000",
        "claimpilot.cache.login-attempts-per-minute=100000"})
@AutoConfigureMockMvc
class AccuracyEvaluationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path PRIVATE_SETS = Path.of(System.getProperty("claimpilot.eval.private-dir",
            "../sample-docs/private/eval"));

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

    /** A document to upload: a file name, its bytes, and "policies" or "receipts". */
    private record Doc(String fileName, byte[] bytes, String collection) {
    }

    private record CaseSet(String name, Map<String, Doc> documents, JsonNode cases) {
    }

    /** Totals for one set, and for all sets together. */
    private static final class Score {
        int questions;
        int statusOk;
        int pageCases;
        int pageOk;
        int contentOk;
        int facts;
        int factOk;
        long totalMs;

        void add(Score other) {
            questions += other.questions;
            statusOk += other.statusOk;
            pageCases += other.pageCases;
            pageOk += other.pageOk;
            contentOk += other.contentOk;
            facts += other.facts;
            factOk += other.factOk;
            totalMs += other.totalMs;
        }
    }

    @Test
    void run() throws Exception {
        List<CaseSet> sets = new ArrayList<>();
        sets.add(publicSet());
        sets.addAll(privateSets());

        Score overall = new Score();
        StringBuilder details = new StringBuilder();
        StringBuilder summary = new StringBuilder("| Set | Answer status | Cited page | Content | Facts | Avg time |\n"
                + "|---|---|---|---|---|---|\n");
        for (CaseSet set : sets) {
            String token = signUp(set.name());
            Score score = new Score();
            details.append("\n## ").append(set.name()).append("\n\n");
            evaluate(set, token, score, details);
            overall.add(score);
            summary.append("| ").append(set.name()).append(" | ").append(pct(score.statusOk, score.questions))
                    .append(" | ").append(pct(score.pageOk, score.pageCases))
                    .append(" | ").append(pct(score.contentOk, score.questions))
                    .append(" | ").append(pct(score.factOk, score.facts))
                    .append(" | ").append(score.totalMs / Math.max(score.questions, 1)).append(" ms |\n");
        }

        StringBuilder report = new StringBuilder("# ClaimPilot accuracy report\n\n")
                .append("| Metric | Result |\n|---|---|\n")
                .append("| Answer status (answered / unclear / not in policy) | ")
                .append(pct(overall.statusOk, overall.questions)).append(" |\n")
                .append("| Cited the expected page | ").append(pct(overall.pageOk, overall.pageCases)).append(" |\n")
                .append("| Answer contains the expected facts | ").append(pct(overall.contentOk, overall.questions))
                .append(" |\n")
                .append("| Key facts extracted correctly | ").append(pct(overall.factOk, overall.facts)).append(" |\n")
                .append("| Average answer time | ").append(overall.totalMs / Math.max(overall.questions, 1))
                .append(" ms |\n\n")
                .append("## By set\n\n").append(summary)
                .append(details);
        Path out = Path.of("target", "eval-report.md");
        Files.writeString(out, report);
        System.out.println(report);
        System.out.println("Report written to " + out.toAbsolutePath());
    }

    private void evaluate(CaseSet set, String token, Score score, StringBuilder details) throws Exception {
        Map<String, String> ids = new LinkedHashMap<>();
        for (Map.Entry<String, Doc> entry : set.documents().entrySet()) {
            ids.put(entry.getKey(), upload(token, entry.getValue()));
        }

        details.append("| Question | Expected | Got | Cited pages | Content | Time |\n|---|---|---|---|---|---|\n");
        for (JsonNode c : set.cases().path("questions")) {
            String policyId = ids.get(c.get("policy").asString());
            long start = System.nanoTime();
            String answer = mvc.perform(auth(token, post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.writeValueAsString(Map.of(
                                    "question", c.get("question").asString(), "policyId", policyId)))))
                    .andReturn().getResponse().getContentAsString();
            long ms = (System.nanoTime() - start) / 1_000_000;

            String status = JsonPath.read(answer, "$.status");
            String text = JsonPath.read(answer, "$.answer");
            List<Integer> pages = JsonPath.read(answer, "$.citations[*].page");
            boolean statusMatch = status.equals(c.get("status").asString());
            boolean pageMatch = true;
            if (c.hasNonNull("page")) {
                score.pageCases++;
                pageMatch = pages.contains(c.get("page").asInt());
                score.pageOk += pageMatch ? 1 : 0;
            }
            boolean contentMatch = contains(text, c.path("mustContain"));
            score.questions++;
            score.statusOk += statusMatch ? 1 : 0;
            score.contentOk += contentMatch ? 1 : 0;
            score.totalMs += ms;
            details.append("| ").append(c.get("question").asString()).append(" | ").append(c.get("status").asString())
                    .append(" | ").append(status).append(statusMatch ? "" : " ✗").append(" | ").append(pages)
                    .append(pageMatch ? "" : " ✗").append(" | ").append(contentMatch ? "yes" : "no ✗")
                    .append(" | ").append(ms).append(" ms |\n");
        }

        details.append("\n| Document | Fact | Expected | Got | Verified |\n|---|---|---|---|---|\n");
        for (JsonNode f : set.cases().path("facts")) {
            String name = f.get("document").asString();
            String body = body(token, "/api/" + set.documents().get(name).collection() + "/" + ids.get(name));
            String key = f.get("key").asString();
            List<String> values = JsonPath.read(body, "$.facts[?(@.key == '" + key + "')].value");
            List<Boolean> verified = JsonPath.read(body, "$.facts[?(@.key == '" + key + "')].verified");
            boolean ok = values.stream().anyMatch(v -> normalize(v).equals(normalize(f.get("value").asString())));
            score.facts++;
            score.factOk += ok ? 1 : 0;
            details.append("| ").append(name).append(" | ").append(key).append(" | ").append(f.get("value").asString())
                    .append(" | ").append(values).append(ok ? "" : " ✗").append(" | ").append(verified).append(" |\n");
        }
    }

    /** Every expected entry must appear; an entry "a|b" is satisfied by either alternative. */
    static boolean contains(String text, JsonNode expected) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (JsonNode entry : expected) {
            boolean any = Stream.of(entry.asString().split("\\|"))
                    .anyMatch(alt -> lower.contains(alt.toLowerCase(Locale.ROOT)));
            if (!any) {
                return false;
            }
        }
        return true;
    }

    private static CaseSet publicSet() throws IOException {
        Map<String, Doc> docs = new LinkedHashMap<>();
        docs.put("cedarview", new Doc(SampleDocuments.SPOUSE_POLICY, SampleDocuments.spousePolicy(), "policies"));
        docs.put("harbourline", new Doc(SampleDocuments.OWN_POLICY, SampleDocuments.ownPolicyFrench(), "policies"));
        docs.put("northgate", new Doc(SampleDocuments.BOOKLET_POLICY, SampleDocuments.bookletPolicy(), "policies"));
        docs.put("receipt", new Doc(SampleDocuments.RECEIPT_PDF, SampleDocuments.receiptPdf(), "receipts"));
        try (InputStream in = AccuracyEvaluationTest.class.getResourceAsStream("/eval/cases.json")) {
            return new CaseSet("Sample documents (fictional)", docs, JSON.readTree(in));
        }
    }

    /** Case sets on the user's own policies, kept out of the repository. */
    private static List<CaseSet> privateSets() throws IOException {
        List<CaseSet> sets = new ArrayList<>();
        if (!Files.isDirectory(PRIVATE_SETS)) {
            return sets;
        }
        try (Stream<Path> files = Files.list(PRIVATE_SETS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                JsonNode cases = JSON.readTree(Files.readString(file));
                Map<String, Doc> docs = new LinkedHashMap<>();
                for (Map.Entry<String, JsonNode> entry : cases.path("documents").properties()) {
                    Path path = file.resolveSibling(entry.getValue().get("file").asString());
                    String kind = entry.getValue().path("kind").asString("policy");
                    docs.put(entry.getKey(), new Doc(path.getFileName().toString(), Files.readAllBytes(path),
                            kind.startsWith("receipt") ? "receipts" : "policies"));
                }
                String name = file.getFileName().toString().replaceFirst("\\.json$", "");
                sets.add(new CaseSet("Private: " + name, docs, cases));
            }
        }
        return sets;
    }

    private String signUp(String setName) throws Exception {
        String username = "eval" + Math.abs(setName.hashCode());
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("username", username, "displayName", "Evaluation",
                                "password", "evaluation-password"))))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s.,]", "").toLowerCase(Locale.ROOT);
    }

    private static String pct(int ok, int total) {
        return total == 0 ? "n/a" : ok + " / " + total + " (" + Math.round(100.0 * ok / total) + "%)";
    }

    private String upload(String token, Doc doc) throws Exception {
        String id = JsonPath.read(mvc.perform(auth(token, multipart("/api/" + doc.collection())
                        .file(new MockMultipartFile("file", doc.fileName(), "application/octet-stream", doc.bytes()))))
                .andReturn().getResponse().getContentAsString(), "$.id");
        long deadline = System.nanoTime() + Duration.ofMinutes(10).toNanos();
        while (System.nanoTime() < deadline) {
            String status = JsonPath.read(body(token, "/api/" + doc.collection() + "/" + id), "$.status");
            if ("READY".equals(status) || "FAILED".equals(status)) {
                return id;
            }
            Thread.sleep(500);
        }
        throw new AssertionError(doc.fileName() + " was not processed in time");
    }

    private String body(String token, String url) throws Exception {
        return mvc.perform(auth(token, get(url))).andReturn().getResponse().getContentAsString();
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B auth(String token, B request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
