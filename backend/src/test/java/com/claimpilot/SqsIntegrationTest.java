package com.claimpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.claimpilot.samples.SampleDocuments;
import com.claimpilot.support.IntegrationTestBase;

/**
 * The AWS design with a real SQS-compatible queue (ElasticMQ): each stored file produces an S3-style
 * "object created" notification, and the worker reading the queue processes the upload.
 */
@TestPropertySource(properties = {
        "claimpilot.events.mode=sqs",
        "claimpilot.events.sqs.publish-uploads=true",
        "claimpilot.events.sqs.visibility-timeout-seconds=2"
})
class SqsIntegrationTest extends IntegrationTestBase {

    @SuppressWarnings("resource")
    static final GenericContainer<?> elasticmq = new GenericContainer<>("softwaremill/elasticmq-native:1.6.14")
            .withExposedPorts(9324)
            .waitingFor(Wait.forListeningPort());

    static {
        elasticmq.start();
    }

    @DynamicPropertySource
    static void queue(DynamicPropertyRegistry registry) {
        String endpoint = "http://" + elasticmq.getHost() + ":" + elasticmq.getMappedPort(9324);
        registry.add("claimpilot.events.sqs.endpoint", () -> endpoint);
        registry.add("claimpilot.events.sqs.queue-url", () -> endpoint + "/000000000000/test-uploads");
    }

    @Test
    void anUploadIsProcessedFromItsQueueNotification() throws Exception {
        String token = login("sam");

        String policyId = upload(token, "policies", SampleDocuments.SPOUSE_POLICY, SampleDocuments.spousePolicy());

        String policy = mvc.perform(as(token, get("/api/policies/" + policyId)))
                .andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(policy, "$.facts[*].key")).contains("POLICY_NUMBER");
        mvc.perform(as(token, delete("/api/policies/" + policyId)));
    }
}
