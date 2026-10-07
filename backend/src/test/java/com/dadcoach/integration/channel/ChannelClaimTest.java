package com.dadcoach.integration.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.AbstractIntegrationTest;
import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherDeletionService;
import com.dadcoach.father.FatherStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The shared-number gateway's claim (playbook §33): Dad Coach claims its fathers - nobody else, no data. */
class ChannelClaimTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired FatherDeletionService deletions;

    private ResultActions claim(String key, String body) throws Exception {
        var request = post("/api/integration/channel/claim").contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(key == null ? request : request.header("X-API-Key", key));
    }

    private ResultActions claim(String phone) throws Exception {
        return claim(CALLBACK_KEY, "{\"phone\":\"" + phone + "\"}");
    }

    @Test
    void aKnownFatherIsClaimedAndTheAnswerCarriesNothingElse() throws Exception {
        Father f = data.father("+19995550900", "דני", FatherStatus.ONBOARDING);
        claim(f.getPhone()).andExpect(status().isOk()).andExpect(content().json("{\"claimed\":true}", true));
        claim("19995550900").andExpect(content().json("{\"claimed\":true}", true));
        claim("+1 999-555-0900").andExpect(content().json("{\"claimed\":true}", true));
    }

    @Test
    void unknownInvalidAndDeletedNumbersAreNotClaimedAndNeverAnError() throws Exception {
        claim("+19995550901").andExpect(status().isOk()).andExpect(content().json("{\"claimed\":false}", true));
        claim("garbage").andExpect(status().isOk()).andExpect(content().json("{\"claimed\":false}", true));
        claim(CALLBACK_KEY, "{}").andExpect(status().isOk()).andExpect(content().json("{\"claimed\":false}", true));

        Father deleted = data.father("+19995550902", "x", FatherStatus.DELETED);
        claim(deleted.getPhone()).andExpect(content().json("{\"claimed\":false}", true));

        // operator-deleted: the father row is gone at once, but his platform deletion is still pending
        Father pending = data.activeFather("+19995550903");
        fake.onTenancy(c -> new com.dadcoach.support.FakeServers.Reply(503, "{}"));
        deletions.deleteByOperator(pending.getId());
        claim(pending.getPhone()).andExpect(content().json("{\"claimed\":false}", true));
    }

    @Test
    void onlyThePlatformCallbackKeyOpensIt() throws Exception {
        Father f = data.activeFather("+19995550904");
        String body = "{\"phone\":\"" + f.getPhone() + "\"}";
        claim(null, body).andExpect(status().isUnauthorized());
        claim("wrong-key-0123456789012345", body).andExpect(status().isUnauthorized());
        claim(TOOL_KEY, body).andExpect(status().isUnauthorized());
        claim(ADMIN_KEY, body).andExpect(status().isUnauthorized());
        assertThat(claim(CALLBACK_KEY, body).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}
