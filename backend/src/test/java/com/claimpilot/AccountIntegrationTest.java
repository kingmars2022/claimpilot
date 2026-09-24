package com.claimpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;

import com.claimpilot.extraction.ExtractionLog;
import com.claimpilot.samples.SampleDocuments;
import com.claimpilot.support.IntegrationTestBase;

/** Sign-in, sign-up, the profile, and deleting every piece of a user's data. */
class AccountIntegrationTest extends IntegrationTestBase {

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

        mvc.perform(as(token, delete("/api/account"))).andExpect(status().isNoContent());

        mvc.perform(as(token, get("/api/auth/me"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "leaving.member", "password", "password123"))))
                .andExpect(status().isUnauthorized());
        assertThat(mongo.count(logsOfUser, ExtractionLog.class)).isZero();
    }
}
