package com.dadcoach.whatsapp;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppController {
  private final WhatsAppService service;
  public WhatsAppController(WhatsAppService service) { this.service = service; }

  @PostMapping("/messages/text")
  public ResponseEntity<Map<?, ?>> sendText(@Valid @RequestBody SendTextRequest request) {
    return ResponseEntity.ok(service.sendText(request.to(), request.message()));
  }
}
