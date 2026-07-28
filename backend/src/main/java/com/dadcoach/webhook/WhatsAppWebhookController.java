package com.dadcoach.webhook;

import com.dadcoach.config.WhatsAppProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController("legacyWhatsAppWebhookController")
@RequestMapping("/webhooks/whatsapp")
public class WhatsAppWebhookController {
  private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
  private final WhatsAppProperties props;
  public WhatsAppWebhookController(WhatsAppProperties props) { this.props = props; }

  @GetMapping
  public ResponseEntity<String> verify(@RequestParam(name="hub.mode") String mode,
      @RequestParam(name="hub.verify_token") String verifyToken,
      @RequestParam(name="hub.challenge") String challenge) {
    if ("subscribe".equals(mode) && props.verifyToken().equals(verifyToken)) return ResponseEntity.ok(challenge);
    return ResponseEntity.status(403).body("Verification failed");
  }

  @PostMapping
  public ResponseEntity<Void> receive(@RequestBody Map<String,Object> payload) {
    log.info("WhatsApp webhook received: {}", payload);
    return ResponseEntity.ok().build();
  }
}
