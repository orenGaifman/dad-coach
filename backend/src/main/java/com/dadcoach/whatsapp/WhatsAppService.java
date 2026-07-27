package com.dadcoach.whatsapp;

import com.dadcoach.config.WhatsAppProperties;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class WhatsAppService {
  private final RestClient client;
  private final WhatsAppProperties props;

  public WhatsAppService(RestClient.Builder builder, WhatsAppProperties props) {
    this.props = props;
    this.client = builder.baseUrl(props.apiBaseUrl()).build();
  }

  public Map<?, ?> sendText(String to, String message) {
    var body = Map.of(
      "messaging_product", "whatsapp",
      "recipient_type", "individual",
      "to", normalize(to),
      "type", "text",
      "text", Map.of("preview_url", false, "body", message)
    );
    return client.post()
      .uri("/{version}/{phoneNumberId}/messages", props.apiVersion(), props.phoneNumberId())
      .contentType(MediaType.APPLICATION_JSON)
      .header("Authorization", "Bearer " + props.accessToken())
      .body(body)
      .retrieve()
      .body(Map.class);
  }

  private String normalize(String phone) { return phone.replaceAll("[^0-9]", ""); }
}
