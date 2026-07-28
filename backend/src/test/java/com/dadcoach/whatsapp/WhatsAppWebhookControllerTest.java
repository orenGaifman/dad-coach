package com.dadcoach.whatsapp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dadcoach.config.WhatsAppProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = WhatsAppWebhookController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.dadcoach\\.api\\..*"
    )
)
@AutoConfigureMockMvc(addFilters = false)
class WhatsAppWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WhatsAppSignatureVerifier signatureVerifier;

    @MockBean
    private WhatsAppProperties properties;

    @Test
    void verifyWebhook_validToken_returnsChallenge() throws Exception {
        when(properties.verifyToken()).thenReturn("my-verify-token");

        mockMvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "my-verify-token")
                        .param("hub.challenge", "challenge-string-123"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge-string-123"));
    }

    @Test
    void verifyWebhook_invalidToken_returns403() throws Exception {
        when(properties.verifyToken()).thenReturn("my-verify-token");

        mockMvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong-token")
                        .param("hub.challenge", "challenge"))
                .andExpect(status().isForbidden());
    }

    @Test
    void handleWebhook_validSignature_returns200() throws Exception {
        byte[] body = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=validhex";

        when(properties.webhookSecret()).thenReturn("secret");
        when(signatureVerifier.isValid(any(byte[].class), eq(signature), eq("secret")))
                .thenReturn(true);

        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", signature)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void handleWebhook_invalidSignature_returns401() throws Exception {
        byte[] body = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=badhex";

        when(properties.webhookSecret()).thenReturn("secret");
        when(signatureVerifier.isValid(any(byte[].class), eq(signature), eq("secret")))
                .thenReturn(false);

        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", signature)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handleWebhook_missingSignatureHeader_returns401() throws Exception {
        byte[] body = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);

        when(properties.webhookSecret()).thenReturn("secret");
        when(signatureVerifier.isValid(any(byte[].class), any(), eq("secret")))
                .thenReturn(false);

        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
