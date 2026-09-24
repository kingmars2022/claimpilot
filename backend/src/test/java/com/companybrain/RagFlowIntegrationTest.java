package com.companybrain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.companybrain.chat.PromptBuilder;
import com.companybrain.support.IntegrationTestBase;

/** Phase 1 flow over HTTP: upload, background indexing, retrieval, cited answer, delete. */
class RagFlowIntegrationTest extends IntegrationTestBase {

    private static final String HANDBOOK = """
            # Employee Handbook

            ## Vacation
            Full-time employees receive 15 paid vacation days in their first year of service.
            From the third year of service, the annual vacation allowance increases to 20 days.

            ## Sick days
            Employees have 7 paid sick days per calendar year.
            """;

    @Test
    void answersFromUploadedDocumentWithSectionCitation() throws Exception {
        String token = login("hana");
        String id = uploadIndexed(token, "handbook.md", HANDBOOK);

        // One chunk per Markdown section: "Vacation" and "Sick days".
        mvc.perform(as(token, get("/api/documents/" + id)))
                .andExpect(jsonPath("$.chunkCount").value(2))
                .andExpect(jsonPath("$.visibleTo").isEmpty());

        String answer = ask(token, "How many vacation days do I get in my first year?", null);

        assertThat((Boolean) JsonPath.read(answer, "$.grounded")).isTrue();
        assertThat((String) JsonPath.read(answer, "$.citations[0].fileName")).isEqualTo("handbook.md");
        assertThat((String) JsonPath.read(answer, "$.citations[0].section")).isEqualTo("Vacation");
        assertThat((String) JsonPath.read(answer, "$.citations[0].snippet"))
                .startsWith("Full-time employees receive 15 paid vacation days");
        assertThat(chatModel.prompts).singleElement().asString()
                .contains("15 paid vacation days")
                .contains("section: Vacation");

        mvc.perform(as(token, delete("/api/documents/" + id))).andExpect(status().isNoContent());
    }

    @Test
    void unrelatedQuestionReturnsFixedTextWithoutCallingModel() throws Exception {
        String token = login("hana");
        String id = uploadIndexed(token, "handbook.md", HANDBOOK);

        String answer = ask(token, "Which espresso machine is on floor seven?", null);

        assertThat((Boolean) JsonPath.read(answer, "$.grounded")).isFalse();
        assertThat((String) JsonPath.read(answer, "$.answer")).isEqualTo(PromptBuilder.NO_ANSWER);
        assertThat(chatModel.prompts).isEmpty();

        mvc.perform(as(token, delete("/api/documents/" + id))).andExpect(status().isNoContent());
    }

    @Test
    void deletedDocumentIsNoLongerSearched() throws Exception {
        String token = login("hana");
        String id = uploadIndexed(token, "handbook.md", HANDBOOK);

        mvc.perform(as(token, delete("/api/documents/" + id))).andExpect(status().isNoContent());

        String answer = ask(token, "How many vacation days do I get in my first year?", null);
        assertThat((String) JsonPath.read(answer, "$.answer")).isEqualTo(PromptBuilder.NO_ANSWER);
        mvc.perform(as(token, get("/api/documents/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnsupportedFileType() throws Exception {
        String token = login("hana");
        mvc.perform(as(token, multipart("/api/documents").file(new MockMultipartFile(
                        "file", "tool.exe", "application/octet-stream", "binary".getBytes(StandardCharsets.UTF_8)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Unsupported file type")));
    }
}
