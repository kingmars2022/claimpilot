package com.claimpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;

import com.claimpilot.events.NotificationService;
import com.claimpilot.extraction.ExtractionLog;
import com.claimpilot.samples.SampleDocuments;
import com.claimpilot.support.IntegrationTestBase;

/** Sign-in, sign-up, the profile, and deleting every piece of a user's data. */
class AccountIntegrationTest extends IntegrationTestBase {

    @Autowired
    private NotificationService notifications;

    @Autowired
    MongoTemplate mongo;

    @Test
    void requestsWithoutAValidTokenAreRejected() throws Exception {
        mvc.perform(get("/api/policies")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/policies").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "fiona", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    void signUpThenFillTheProfile() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "new.member", "displayName", "New Member",
                                "password", "password123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "new.member", "displayName", "Again",
                                "password", "password123"))))
                .andExpect(status().isBadRequest());

        mvc.perform(as(token, get("/api/profile"))).andExpect(jsonPath("$.fullName").doesNotExist());
        mvc.perform(as(token, put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "New Member", "dateOfBirth", "1990-02-03",
                                "city", "Laval", "province", "QC")))))
                .andExpect(status().isOk());
        mvc.perform(as(token, get("/api/profile")))
                .andExpect(jsonPath("$.fullName").value("New Member"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-02-03"));
    }

    @Test
    void deletingTheAccountRemovesEverything() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "leaving.member", "displayName", "Leaving",
                                "password", "password123"))))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        Integer userId = JsonPath.read(body, "$.user.id");
        upload(token, "receipts", SampleDocuments.RECEIPT_PDF, SampleDocuments.receiptPdf());
        Query logsOfUser = Query.query(Criteria.where("ownerId").is(userId.longValue()));
        assertThat(mongo.count(logsOfUser, ExtractionLog.class)).isPositive();
        for (int i = 0; i < 50 && notifications.recent(userId.longValue()).isEmpty(); i++) {
            Thread.sleep(100);
        }
        assertThat(notifications.recent(userId.longValue())).as("the upload was notified").isNotEmpty();

        mvc.perform(as(token, delete("/api/account"))).andExpect(status().isNoContent());

        assertThat(notifications.recent(userId.longValue())).as("notifications name the files").isEmpty();

        mvc.perform(as(token, get("/api/auth/me"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "leaving.member", "password", "password123"))))
                .andExpect(status().isUnauthorized());
        assertThat(mongo.count(logsOfUser, ExtractionLog.class)).isZero();
    }

    @Test
    void theNotificationStreamOpensOnlyWithAOneMinuteTicket() throws Exception {
        String token = login("sam");
        String ticket = JsonPath.read(mvc.perform(as(token, post("/api/notifications/ticket")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.value");

        mvc.perform(get("/api/notifications/stream").param("access_token", ticket))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
        // A session token never goes in a URL, and a ticket opens nothing but the stream.
        mvc.perform(get("/api/notifications/stream").param("access_token", token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications/stream")).andExpect(status().isUnauthorized());
        mvc.perform(as(ticket, get("/api/policies"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/policies").param("access_token", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void activityLogListsWhatTheUserDid() throws Exception {
        String token = login("sam");
        mvc.perform(as(token, put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Sam Okafor")))))
                .andExpect(status().isOk());

        String activity = mvc.perform(as(token, get("/api/audit"))).andReturn().getResponse().getContentAsString();

        java.util.List<String> actions = JsonPath.read(activity, "$[*].action");
        assertThat(actions).startsWith("PROFILE_UPDATED").contains("SIGNED_IN");
    }
}
