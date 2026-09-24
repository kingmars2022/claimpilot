package com.claimpilot.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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

/**
 * Boots the whole application over HTTP (MockMvc) against real pgvector and MongoDB containers.
 * The AI models are replaced by fakes, so the tests need Docker but not Ollama.
 * <p>
 * The containers are started once and shared by every test class, which also share one Spring
 * context. Demo users (fiona, sam) come from the V2 Flyway migration and use the password "demo1234".
 */
@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "claimpilot.retrieval.similarity-threshold=0.1"
})
@AutoConfigureMockMvc
@Import(IntegrationTestBase.FakeModels.class)
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "demo1234";

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @ServiceConnection
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    private static final Path UPLOADS;

    static {
        postgres.start();
        mongo.start();
        try {
            UPLOADS = Files.createTempDirectory("claimpilot-uploads");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("claimpilot.storage.local-root", UPLOADS::toString);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected FakeChatModel chatModel;

    @BeforeEach
    void resetChatModel() {
        chatModel.reset();
    }

    // ---------- HTTP helpers ----------

    protected String login(String username) throws Exception {
        return login(username, PASSWORD);
    }

    protected String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    protected static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(String token, B request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /** Uploads a file to /api/policies or /api/receipts and waits until it is processed; returns its id. */
    protected String upload(String token, String collection, String fileName, byte[] content) throws Exception {
        String body = mvc.perform(as(token, multipart("/api/" + collection)
                        .file(new MockMultipartFile("file", fileName, "application/octet-stream", content))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        waitUntilReady(token, collection, id);
        return id;
    }

    protected void waitUntilReady(String token, String collection, String id) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (System.nanoTime() < deadline) {
            String body = mvc.perform(as(token, get("/api/" + collection + "/" + id)))
                    .andReturn().getResponse().getContentAsString();
            String status = JsonPath.read(body, "$.status");
            if ("FAILED".equals(status)) {
                throw new AssertionError("Processing failed: " + JsonPath.read(body, "$.errorMessage"));
            }
            if ("READY".equals(status)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError(collection + " " + id + " was not processed in time");
    }

    /** Asks a question about a policy and returns the raw JSON answer. */
    protected String ask(String token, String policyId, String question, String conversationId) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("question", question, "policyId", policyId));
        if (conversationId != null) {
            body.put("conversationId", conversationId);
        }
        return mvc.perform(as(token, post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(json(body))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** Minimal JSON writer for flat test request bodies. */
    protected static String json(Map<String, ?> values) {
        List<String> fields = new ArrayList<>();
        values.forEach((key, value) -> fields.add("\"" + key + "\":" + jsonValue(value)));
        return "{" + String.join(",", fields) + "}";
    }

    private static String jsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof List<?> list) {
            return "[" + String.join(",", list.stream().map(IntegrationTestBase::jsonValue).toList()) + "]";
        }
        return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ---------- Fake AI models ----------

    @TestConfiguration
    public static class FakeModels {

        @Bean
        EmbeddingModel embeddingModel() {
            return new HashingEmbeddingModel();
        }

        @Bean
        FakeChatModel chatModel() {
            return new FakeChatModel();
        }
    }

    /**
     * Stands in for qwen3. Recognizes each kind of prompt the application sends and replies the way
     * a well-behaved model would, including one deliberately invented value so the tests can check
     * that verification catches it. Every prompt is recorded by kind.
     */
    public static class FakeChatModel implements ChatModel {

        public enum Kind { EXTRACT_POLICY, EXTRACT_RECEIPT, MAP_FORM, GUIDE, ANSWER, TRANSLATE }

        public final Map<Kind, List<String>> prompts = new java.util.concurrent.ConcurrentHashMap<>();
        /** Reply to the next question; set by a test. */
        public volatile String nextAnswer = "STATUS: ANSWERED\nPhysiotherapy is reimbursed at 80%, up to $600 a year [1].";

        void reset() {
            prompts.clear();
            nextAnswer = "STATUS: ANSWERED\nPhysiotherapy is reimbursed at 80%, up to $600 a year [1].";
        }

        public List<String> calls(Kind kind) {
            return prompts.getOrDefault(kind, List.of());
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            String text = prompt.getContents();
            Kind kind = kindOf(text);
            prompts.computeIfAbsent(kind, k -> new CopyOnWriteArrayList<>()).add(text);
            String reply = switch (kind) {
                case EXTRACT_POLICY -> text.contains("CEDARVIEW") ? CEDARVIEW_FACTS
                        : text.contains("HARBOURLINE") ? HARBOURLINE_FACTS : "{}";
                case EXTRACT_RECEIPT -> RECEIPT_FACTS;
                case MAP_FORM -> text.contains("- f01:") ? FRENCH_FORM_MAPPING : FORM_MAPPING;
                case GUIDE -> GUIDE;
                case ANSWER -> nextAnswer;
                case TRANSLATE -> "Is physiotherapy covered by my plan?";
            };
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }

        private static Kind kindOf(String text) {
            if (text.contains("Form fields (name: label)")) {
                return Kind.MAP_FORM;
            }
            if (text.contains("claim checklist")) {
                return Kind.GUIDE;
            }
            if (text.contains("from the insurance policy below")) {
                return Kind.EXTRACT_POLICY;
            }
            if (text.contains("from the receipt below")) {
                return Kind.EXTRACT_RECEIPT;
            }
            if (text.startsWith("Translate this question")) {
                return Kind.TRANSLATE;
            }
            return Kind.ANSWER;
        }

        static final String CEDARVIEW_FACTS = """
                {"INSURER_NAME": {"value": "Cedarview Assurance", "quote": "CEDARVIEW ASSURANCE"},
                 "INSURER_PHONE": {"value": "1-800-555-0199", "quote": "Member Services: 1-800-555-0199"},
                 "INSURER_HOURS": {"value": "Monday to Friday, 8 a.m. to 8 p.m. (Eastern Time)",
                                   "quote": "Hours: Monday to Friday, 8 a.m. to 8 p.m. (Eastern Time)"},
                 "POLICY_NUMBER": {"value": "CV-88213-02", "quote": "Group policy number: CV-88213-02"},
                 "CERTIFICATE_NUMBER": {"value": "55190336", "quote": "Certificate number: 55190336"},
                 "PLAN_MEMBER_NAME": {"value": "Marc Gagnon", "quote": "Plan member: Marc Gagnon"},
                 "PLAN_SPONSOR": {"value": "Rive-Nord Logistics Ltd.", "quote": "Plan sponsor: Rive-Nord Logistics Ltd."},
                 "CLAIMS_ADDRESS": {"value": "P.O. Box 77, Toronto, ON", "quote": "Mail claims to P.O. Box 77, Toronto, ON"}}
                """;

        static final String HARBOURLINE_FACTS = """
                {"INSURER_NAME": {"value": "Harbourline Vie", "quote": "HARBOURLINE VIE"},
                 "INSURER_PHONE": {"value": "1-888-555-0123", "quote": "Service à la clientèle : 1-888-555-0123"},
                 "POLICY_NUMBER": {"value": "HL-204518", "quote": "Numéro de police collective : HL-204518"},
                 "CERTIFICATE_NUMBER": {"value": "7730142", "quote": "Numéro de certificat : 7730142"},
                 "PLAN_MEMBER_NAME": {"value": "Fiona Tremblay", "quote": "Adhérente : Fiona Tremblay"},
                 "PLAN_SPONSOR": {"value": "Atelier Boréal Design inc.", "quote": "Preneur (employeur) : Atelier Boréal Design inc."}}
                """;

        static final String RECEIPT_FACTS = """
                {"PROVIDER_NAME": {"value": "Clinique Physio Plateau", "quote": "CLINIQUE PHYSIO PLATEAU"},
                 "PATIENT_NAME": {"value": "Fiona Tremblay", "quote": "Patient: Fiona Tremblay"},
                 "SERVICE_DATE": {"value": "2026-03-05", "quote": "Date of service: March 5, 2026"},
                 "SERVICE_TYPE": {"value": "Physiotherapy - follow-up treatment (45 min)",
                                  "quote": "Service: Physiotherapy - follow-up treatment (45 min)"},
                 "AMOUNT_CHARGED": {"value": "120.00", "quote": "Total charged: $120.00"},
                 "AMOUNT_PAID_BY_OTHER_PLAN": {"value": "84.00", "quote": "Paid by Harbourline Vie (direct billing): $84.00"},
                 "RECEIPT_NUMBER": {"value": "R-2026-0318", "quote": "Receipt no.: R-2026-0318"}}
                """;

        /** Includes a signature field mapped to a name, which the application must refuse. */
        static final String FRENCH_FORM_MAPPING = """
                {"f01": "MEMBER_NAME", "f02": "POLICY_NUMBER", "f03": "CERTIFICATE_NUMBER", "f04": "PATIENT_NAME",
                 "f05": "PATIENT_DOB", "f06": "PATIENT_RELATIONSHIP", "f07": "PATIENT_ADDRESS", "f08": "PATIENT_PHONE",
                 "f09": "OTHER_INSURER", "f10": "OTHER_POLICY_NUMBER", "f11": "PROVIDER_NAME", "f12": "RECEIPT_NUMBER",
                 "f13": "SERVICE_DATE", "f14": "SERVICE_TYPE", "f15": "AMOUNT_CHARGED",
                 "f16": "AMOUNT_PAID_BY_OTHER_PLAN", "f17": "AMOUNT_CLAIMED", "f18": "DECLARATION",
                 "f19": "SIGNATURE", "f20": "PATIENT_DOB"}
                """;

        static final String FORM_MAPPING = """
                {"txtField_01": "MEMBER_NAME", "txtField_02": "POLICY_NUMBER", "txtField_03": "CERTIFICATE_NUMBER",
                 "txtField_04": "PLAN_SPONSOR", "txtField_05": "PATIENT_NAME", "txtField_06": "PATIENT_DOB",
                 "txtField_07": "PATIENT_RELATIONSHIP", "txtField_08": "PATIENT_ADDRESS", "txtField_09": "OTHER_INSURER",
                 "txtField_10": "OTHER_POLICY_NUMBER", "txtField_11": "OTHER_CERTIFICATE_NUMBER",
                 "txtField_12": "PROVIDER_NAME", "txtField_13": "SERVICE_DATE", "txtField_14": "SERVICE_TYPE",
                 "txtField_15": "AMOUNT_CHARGED", "txtField_16": "AMOUNT_PAID_BY_OTHER_PLAN",
                 "txtField_17": "AMOUNT_CLAIMED", "txtField_19": "NONE", "chkField_01": "DECLARATION",
                 "sigField_01": "MEMBER_NAME", "txtField_18": "SIGNATURE_DATE"}
                """;

        static final String GUIDE = """
                {"deadlines": [{"text": "Submit the claim within 12 months of the date of service", "source": 1},
                               {"text": "Submit within 30 days", "source": 99}],
                 "documents": [{"text": "The original itemized receipt", "source": 1},
                               {"text": "The Explanation of Benefits from the first plan", "source": 1}],
                 "submission": [{"text": "Use the Cedarview Benefits app or mail the claim form", "source": 1}],
                 "coverage": [{"text": "Physiotherapy: 80%, up to $600 a year", "source": 1}],
                 "preApproval": {"required": "NO", "text": "No physician referral is needed for physiotherapy", "source": 1}}
                """;
    }

    /**
     * Deterministic stand-in for bge-m3: a normalized bag of hashed words in 1024 dimensions.
     * Texts that share words get a high cosine similarity; unrelated texts score near zero.
     */
    static class HashingEmbeddingModel implements EmbeddingModel {

        private static final int DIMENSIONS = 1024;

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            List<String> texts = request.getInstructions();
            for (int i = 0; i < texts.size(); i++) {
                embeddings.add(new Embedding(vector(texts.get(i)), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        @Override
        public int dimensions() {
            return DIMENSIONS;
        }

        private static float[] vector(String text) {
            float[] v = new float[DIMENSIONS];
            for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (word.length() > 3) {
                    v[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
                }
            }
            double norm = 0;
            for (float x : v) {
                norm += x * x;
            }
            if (norm == 0) {
                v[0] = 1f;
                return v;
            }
            float scale = (float) (1 / Math.sqrt(norm));
            for (int i = 0; i < DIMENSIONS; i++) {
                v[i] *= scale;
            }
            return v;
        }
    }
}
