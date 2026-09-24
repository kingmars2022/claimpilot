package com.claimpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

import com.claimpilot.samples.SampleDocuments;
import com.claimpilot.support.IntegrationTestBase;

/**
 * Phase 3: the event-driven path. Uploads go encrypted to an S3-compatible server (RustFS, standing in for Amazon S3), a Kafka
 * event hands them to a worker, and a second event brings the result back as a notification.
 */
@TestPropertySource(properties = {
        "claimpilot.events.mode=kafka",
        "claimpilot.storage.type=s3",
        "claimpilot.storage.s3.bucket=test-uploads"
})
class EventDrivenIntegrationTest extends IntegrationTestBase {

    private static final String ACCESS_KEY = "claimpilot";
    private static final String SECRET_KEY = "claimpilot-secret";

    @ServiceConnection
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    /** RustFS: a free, Apache-licensed S3-compatible server (MinIO no longer publishes free Docker images). */
    @SuppressWarnings("resource")
    static final GenericContainer<?> s3Server = new GenericContainer<>("rustfs/rustfs:1.0.0")
            .withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
            .withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
            .withExposedPorts(9000)
            .waitingFor(Wait.forListeningPort());

    static {
        kafka.start();
        s3Server.start();
    }

    @DynamicPropertySource
    static void s3(DynamicPropertyRegistry registry) {
        registry.add("claimpilot.storage.s3.endpoint", EventDrivenIntegrationTest::s3Url);
        registry.add("claimpilot.storage.s3.access-key", () -> ACCESS_KEY);
        registry.add("claimpilot.storage.s3.secret-key", () -> SECRET_KEY);
    }

    private static String s3Url() {
        return "http://" + s3Server.getHost() + ":" + s3Server.getMappedPort(9000);
    }

    @Test
    void uploadIsStoredEncryptedInS3ProcessedByAWorkerAndNotified() throws Exception {
        String token = login("sam");
        String policyId = upload(token, "policies", SampleDocuments.SPOUSE_POLICY, SampleDocuments.spousePolicy());

        try (S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(s3Url()))
                .region(Region.CA_CENTRAL_1)
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .build()) {
            List<S3Object> objects = s3.listObjectsV2(b -> b.bucket("test-uploads")).contents();
            assertThat(objects).anySatisfy(o -> assertThat(o.key()).endsWith(SampleDocuments.SPOUSE_POLICY));
            String key = objects.stream().filter(o -> o.key().endsWith(SampleDocuments.SPOUSE_POLICY))
                    .findFirst().orElseThrow().key();
            byte[] stored = s3.getObject(b -> b.bucket("test-uploads").key(key), ResponseTransformer.toBytes())
                    .asByteArray();
            assertThat(new String(stored, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("CPE1");
        }

        String notifications = "[]";
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            notifications = mvc.perform(as(token, get("/api/notifications"))).andReturn().getResponse()
                    .getContentAsString();
            List<String> ids = JsonPath.read(notifications, "$[*].documentId");
            if (ids.contains(policyId)) {
                break;
            }
            Thread.sleep(200);
        }
        assertThat((List<String>) JsonPath.read(notifications, "$[?(@.documentId == '" + policyId + "')].type"))
                .containsExactly("DOCUMENT_READY");

        String activity = mvc.perform(as(token, get("/api/audit"))).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(activity, "$[*].action"))
                .contains("SIGNED_IN", "DOCUMENT_UPLOADED", "DOCUMENT_READY");
    }
}
