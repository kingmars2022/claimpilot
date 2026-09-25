package com.claimpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.claimpilot.audit.AuditService;
import com.claimpilot.events.NotificationService;
import com.claimpilot.extraction.ExtractionLog;
import com.claimpilot.samples.SampleDocuments;
import com.claimpilot.support.IntegrationTestBase;
import com.claimpilot.user.AccountService;

/** Sign-in, sign-up, the profile, and deleting every piece of a user's data. */
class AccountIntegrationTest extends IntegrationTestBase {

    @Autowired
    private NotificationService notifications;
    @Autowired
    private AccountService accounts;
    @Autowired
    private AuditService audit;
    @Autowired
    private JdbcTemplate jdbc;

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
                .andExpect(jsonPath("$.dateOfBirth").value("1990-02-03"))
                .andExpect(jsonPath("$.custody").value("TOGETHER"));

        mvc.perform(as(token, put("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "New Member", "custody", "JOINT",
                                "otherParentName", "Luc Bergeron", "otherParentDateOfBirth", "1990-08-03")))))
                .andExpect(status().isOk());
        mvc.perform(as(token, get("/api/profile")))
                .andExpect(jsonPath("$.custody").value("JOINT"))
                .andExpect(jsonPath("$.otherParentName").value("Luc Bergeron"))
                .andExpect(jsonPath("$.otherParentDateOfBirth").value("1990-08-03"));
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
    void anInterruptedDeletionLocksTheAccountAndIsFinishedInTheBackground() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "half.deleted", "displayName", "Half",
                                "password", "password123"))))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        Integer userId = JsonPath.read(body, "$.user.id");
        upload(token, "receipts", SampleDocuments.RECEIPT_PDF, SampleDocuments.receiptPdf());
        // The server stopped right after recording the request.
        jdbc.update("UPDATE users SET deletion_requested_at = now() WHERE id = ?", userId);

        mvc.perform(as(token, get("/api/receipts"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "half.deleted", "password", "password123"))))
                .andExpect(status().isUnauthorized());

        accounts.resumePendingDeletions();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents WHERE owner_id = ?", Integer.class, userId))
                .isZero();
    }

    @Test
    void oldActivityIsDeletedAfterTheRetentionPeriod() throws Exception {
        String token = login("sam");
        Long samId = jdbc.queryForObject("SELECT id FROM users WHERE username = 'sam'", Long.class);
        jdbc.update("INSERT INTO audit_events (user_id, action, created_at) VALUES (?, 'SIGNED_IN', "
                + "now() - interval '400 days')", samId);

        assertThat(audit.purgeExpired()).isPositive();

        Integer old = jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE user_id = ? "
                + "AND created_at < now() - interval '365 days'", Integer.class, samId);
        assertThat(old).isZero();
        String activity = mvc.perform(as(token, get("/api/audit"))).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(activity, "$[*].action")).contains("SIGNED_IN");
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
