package com.companybrain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.companybrain.support.IntegrationTestBase;

/** Conversation history in MongoDB and follow-up rewriting. */
class ConversationIntegrationTest extends IntegrationTestBase {

    private static final String HANDBOOK = """
            # Employee Handbook

            ## Vacation
            Full-time employees receive 15 paid vacation days in their first year of service.
            From the third year of service, the annual vacation allowance increases to 20 days.
            """;

    @Test
    void followUpIsRewrittenSearchedAndSaved() throws Exception {
        String manager = login("hana");
        String docId = uploadIndexed(manager, "handbook.md", HANDBOOK);
        String token = login("ivan");

        String first = ask(token, "How many vacation days do I get in my first year?", null);
        String conversationId = JsonPath.read(first, "$.conversationId");
        assertThat((Object) JsonPath.read(first, "$.searchQuery")).isNull();
        assertThat(chatModel.rewritePrompts).isEmpty();  // nothing to rewrite on the first question

        chatModel.rewriteTo = "\"How many vacation days do I get from the third year of service?\"";
        String second = ask(token, "And from the third year?", conversationId);

        assertThat((String) JsonPath.read(second, "$.conversationId")).isEqualTo(conversationId);
        assertThat((String) JsonPath.read(second, "$.searchQuery"))
                .isEqualTo("How many vacation days do I get from the third year of service?");
        assertThat(chatModel.rewritePrompts).singleElement().asString()
                .contains("Employee: How many vacation days do I get in my first year?")
                .contains("Assistant: Here is what the policy says")
                .contains("Follow-up question: And from the third year?");
        assertThat(chatModel.answerPrompts.getLast())
                .contains("Question: How many vacation days do I get from the third year of service?");

        mvc.perform(as(token, get("/api/conversations/" + conversationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("How many vacation days do I get in my first year?"))
                .andExpect(jsonPath("$.messages.length()").value(4))
                .andExpect(jsonPath("$.messages[2].content").value("And from the third year?"))
                .andExpect(jsonPath("$.messages[2].searchQuery")
                        .value("How many vacation days do I get from the third year of service?"))
                .andExpect(jsonPath("$.messages[3].sender").value("ASSISTANT"))
                .andExpect(jsonPath("$.messages[3].citations[0].section").value("Vacation"));

        String list = mvc.perform(as(token, get("/api/conversations")))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(list, "$[*].id");
        assertThat(ids.getFirst()).isEqualTo(conversationId);  // most recently updated first

        mvc.perform(as(manager, delete("/api/documents/" + docId))).andExpect(status().isNoContent());
    }

    @Test
    void conversationsArePrivateToTheirOwner() throws Exception {
        String owner = login("ivan");
        String other = login("fiona");
        String answer = ask(owner, "Is there anything about parking?", null);
        String conversationId = JsonPath.read(answer, "$.conversationId");

        mvc.perform(as(other, get("/api/conversations/" + conversationId))).andExpect(status().isNotFound());
        mvc.perform(as(other, delete("/api/conversations/" + conversationId))).andExpect(status().isNotFound());
        mvc.perform(as(other, post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("question", "hello", "conversationId", conversationId)))))
                .andExpect(status().isNotFound());

        mvc.perform(as(owner, delete("/api/conversations/" + conversationId))).andExpect(status().isNoContent());
        mvc.perform(as(owner, get("/api/conversations/" + conversationId))).andExpect(status().isNotFound());
    }
}
