package com.claimpilot.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class S3EventMessagesTest {

    @Test
    void keysAreReadFromAnS3NotificationAndDecoded() {
        String body = """
                {"Records":[{"eventVersion":"2.1","eventSource":"aws:s3","eventName":"ObjectCreated:Put",
                  "s3":{"bucket":{"name":"claimpilot-uploads"},
                        "object":{"key":"3f1c/physio+receipt+%C3%A9t%C3%A9.pdf","size":1307}}}]}
                """;

        assertThat(S3EventMessages.createdKeys(body)).containsExactly("3f1c/physio receipt été.pdf");
    }

    @Test
    void testEventsAndOtherMessagesHaveNoKeys() {
        assertThat(S3EventMessages.createdKeys("{\"Service\":\"Amazon S3\",\"Event\":\"s3:TestEvent\"}")).isEmpty();
        assertThat(S3EventMessages.createdKeys("not json")).isEmpty();
        assertThat(S3EventMessages.createdKeys("{\"Records\":[{\"eventName\":\"ObjectRemoved:Delete\","
                + "\"s3\":{\"object\":{\"key\":\"a/b.pdf\"}}}]}")).isEmpty();
    }

    @Test
    void aMessageWrittenForLocalUseReadsBackTheSameKey() {
        String key = "5a2e/harbourline police été.pdf";

        assertThat(S3EventMessages.createdKeys(S3EventMessages.objectCreated("local", key))).containsExactly(key);
    }
}
