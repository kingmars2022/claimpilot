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

/** Conversation history in MongoDB and follow-up questions. */
class ConversationIntegrationTest extends IntegrationTestBase {

    private static final String HANDBOOK = """
            # Employee Handbook

            ## Vacation
            Full-time employees receive 15 paid vacation days in their first year of service.
            From the third year of service, the annual vacation allowance increases to 20 days.
            """;

    @Test
    void followUpIsAnsweredWithConversationContextInOneModelCall() throws Exception {
        String manager = login("hana");
        String docId = uploadIndexed(manager, "handbook.md", HANDBOOK);
        String token = login("ivan");

        String first = ask(token, "How many vacation days do I get in my first year?", null);
        String conversationId = JsonPath.read(first, "$.conversationId");
        assertThat(chatModel.prompts).singleElement().asString().doesNotContain("Earlier in this conversation");

        // "Allowance" appears only in the handbook; "that" and "later" need the earlier question.
        String second = ask(token, "Does that allowance change later?", conversationId);

        assertThat((String) JsonPath.read(second, "$.conversationId")).isEqualTo(conversationId);
        assertThat((Boolean) JsonPath.read(second, "$.grounded")).isTrue();
        // One model call per question: no separate rewrite step.
        assertThat(chatModel.prompts).hasSize(2);
        assertThat(chatModel.prompts.getLast())
                .contains("Earlier in this conversation:")
                .contains("Employee: How many vacation days do I get in my first year?")
                .contains("Assistant: Here is what the policy says .")  // old [1] markers removed
                .contains("15 paid vacation days")
                .contains("Question: Does that allowance change later?");

        mvc.perform(as(token, get("/api/conversations/" + conversationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("How many vacation days do I get in my first year?"))
                .andExpect(jsonPath("$.messages.length()").value(4))
                .andExpect(jsonPath("$.messages[2].content").value("Does that allowance change later?"))
                .andExpect(jsonPath("$.messages[3].sender").value("ASSISTANT"))
                .andExpect(jsonPath("$.messages[3].citations[0].section").value("Vacation"));

        String list = mvc.perform(as(token, get("/api/conversations")))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(list, "$[*].id");
        assertThat(ids.getFirst()).isEqualTo(conversationId);  // most recently updated first

        mvc.perform(as(manager, delete("/api/documents/" + docId))).andExpect(status().isNoContent());
    }

    @Test
    void followUpThatOnlyMakesSenseWithContextStillFindsTheSource() throws Exception {
        String manager = login("hana");
        String docId = uploadIndexed(manager, "handbook.md", HANDBOOK);
        String token = login("ivan");

        String first = ask(token, "How many vacation days do I get in my first year?", null);
        String conversationId = JsonPath.read(first, "$.conversationId");

        // On its own this matches nothing; searched together with the previous question it does.
        String alone = ask(token, "What about seniors?", null);
        assertThat((Boolean) JsonPath.read(alone, "$.grounded")).isFalse();
        String followUp = ask(token, "What about seniors?", conversationId);
        assertThat((Boolean) JsonPath.read(followUp, "$.grounded")).isTrue();

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
