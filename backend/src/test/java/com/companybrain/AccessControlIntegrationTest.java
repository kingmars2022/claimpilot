package com.companybrain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.companybrain.support.IntegrationTestBase;

/** Authentication, role checks and permission-aware retrieval. */
class AccessControlIntegrationTest extends IntegrationTestBase {

    private static final String SALARY_BANDS = """
            # Compensation

            ## Salary bands
            Salary band four for senior engineers ranges from 85000 to 105000 dollars per year.
            """;

    private static final String QUESTION = "What is the salary band range for senior engineers?";

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/conversations").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejectedWithGenericMessage() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "ivan", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "nobody", "password", "whatever"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    void meReturnsRoleAndDepartment() throws Exception {
        mvc.perform(as(login("ivan"), get("/api/auth/me")))
                .andExpect(jsonPath("$.username").value("ivan"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.department.code").value("IT"));
    }

    @Test
    void rolesLimitWhatEachUserCanDo() throws Exception {
        String employee = login("ivan");
        String manager = login("hana");
        String admin = login("admin");

        mvc.perform(as(employee, get("/api/documents"))).andExpect(status().isForbidden());
        mvc.perform(as(employee, get("/api/admin/users"))).andExpect(status().isForbidden());
        mvc.perform(as(manager, get("/api/documents"))).andExpect(status().isOk());
        mvc.perform(as(manager, get("/api/admin/users"))).andExpect(status().isForbidden());
        mvc.perform(as(admin, get("/api/admin/users"))).andExpect(status().isOk());
    }

    @Test
    void departmentDocumentOnlyReachesThatDepartment() throws Exception {
        String hrManager = login("hana");      // HR
        String itEmployee = login("ivan");     // IT
        String admin = login("admin");
        Long hr = departmentId(hrManager, "HR");
        Long it = departmentId(hrManager, "IT");

        String id = uploadIndexed(hrManager, "salary-bands.md", SALARY_BANDS, hr);

        assertThat(grounded(ask(hrManager, QUESTION, null))).isTrue();
        assertThat(grounded(ask(admin, QUESTION, null))).isTrue();

        int promptsBefore = chatModel.prompts.size();
        String itAnswer = ask(itEmployee, QUESTION, null);
        assertThat(grounded(itAnswer)).isFalse();
        // The restricted chunk never reached the model: no answer call was made at all.
        assertThat(chatModel.prompts).hasSize(promptsBefore);

        // Company-wide: the IT employee can now find it. Only chunk metadata changes, no re-indexing.
        setVisibility(hrManager, id, List.of());
        assertThat(grounded(ask(itEmployee, QUESTION, null))).isTrue();

        // IT only: HR loses access, IT keeps it.
        setVisibility(hrManager, id, List.of(it));
        assertThat(grounded(ask(itEmployee, QUESTION, null))).isTrue();
        assertThat(grounded(ask(hrManager, QUESTION, null))).isFalse();

        mvc.perform(as(hrManager, delete("/api/documents/" + id))).andExpect(status().isNoContent());
    }

    @Test
    void departmentChangeAppliesToExistingToken() throws Exception {
        String admin = login("admin");
        Long hr = departmentId(admin, "HR");
        Long finance = departmentId(admin, "FINANCE");
        String userJson = mvc.perform(as(admin, post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "temp.worker", "displayName", "Temp Worker",
                                "password", "password123", "role", "EMPLOYEE", "departmentId", finance)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer userId = JsonPath.read(userJson, "$.id");
        String temp = login("temp.worker", "password123");
        String docId = uploadIndexed(admin, "salary-bands.md", SALARY_BANDS, hr);

        assertThat(grounded(ask(temp, QUESTION, null))).isFalse();

        mvc.perform(as(admin, put("/api/admin/users/" + userId).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "Temp Worker", "role", "EMPLOYEE", "departmentId", hr)))))
                .andExpect(status().isOk());

        // Same token as before: the department is read from the database on each request.
        assertThat(grounded(ask(temp, QUESTION, null))).isTrue();

        mvc.perform(as(admin, delete("/api/documents/" + docId))).andExpect(status().isNoContent());
        mvc.perform(as(admin, delete("/api/admin/users/" + userId))).andExpect(status().isNoContent());
        // A deleted account's token stops working immediately.
        mvc.perform(as(temp, get("/api/auth/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    void adminGuardsAgainstMistakes() throws Exception {
        String admin = login("admin");
        mvc.perform(as(admin, post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "ivan", "displayName", "Duplicate",
                                "password", "password123", "role", "EMPLOYEE")))))
                .andExpect(status().isConflict());

        String me = mvc.perform(as(admin, get("/api/auth/me"))).andReturn().getResponse().getContentAsString();
        Integer adminId = JsonPath.read(me, "$.id");
        mvc.perform(as(admin, delete("/api/admin/users/" + adminId))).andExpect(status().isBadRequest());
    }

    private void setVisibility(String token, String documentId, List<Long> departmentIds) throws Exception {
        mvc.perform(as(token, put("/api/documents/" + documentId + "/visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("departmentIds", departmentIds)))))
                .andExpect(status().isOk());
    }

    private static boolean grounded(String answerJson) {
        return JsonPath.read(answerJson, "$.grounded");
    }
}
