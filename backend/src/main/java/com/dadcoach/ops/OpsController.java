package com.dadcoach.ops;

import com.dadcoach.auth.LoginLinkService;
import com.dadcoach.auth.PhoneInput;
import com.dadcoach.auth.StaffUser;
import com.dadcoach.auth.StaffUserRepository;
import com.dadcoach.web.common.WebException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The operator surface behind DADCOACH_OPS_API_KEY (playbook §49: never make WhatsApp a prerequisite for local
 * login). Closed when the key is unset. No names or phones are echoed back, and a login link is returned to the
 * caller only - it is never sent anywhere.
 */
@RestController
@RequestMapping("/api/ops")
public class OpsController {

    public record BootstrapAdminRequest(@NotBlank @Size(max = 40) String phone, @NotBlank @Size(max = 120) String name) {
    }

    public record BootstrapAdminResponse(UUID staffUserId, boolean created) {
    }

    public record LoginLinkRequest(@NotBlank @Size(max = 40) String phone) {
    }

    public record LoginLinkResponse(String loginUrl, Instant expiresAt) {
    }

    private final StaffUserRepository staff;
    private final LoginLinkService links;
    private final Clock clock;

    public OpsController(StaffUserRepository staff, LoginLinkService links, Clock clock) {
        this.staff = staff;
        this.links = links;
        this.clock = clock;
    }

    /** 201 when the staff user was created, 200 when an existing one was (re)confirmed and re-activated. */
    @PostMapping("/bootstrap-admin")
    @Transactional
    public ResponseEntity<BootstrapAdminResponse> bootstrapAdmin(@Valid @RequestBody BootstrapAdminRequest request) {
        String phone = PhoneInput.normalize(request.phone())
                .orElseThrow(() -> WebException.badRequest("INVALID_PHONE", "phone must be a full number"));
        String name = request.name().strip();
        return staff.findByPhone(phone)
                .map(existing -> {
                    existing.confirm(name, clock.instant());
                    return ResponseEntity.ok(new BootstrapAdminResponse(existing.getId(), false));
                })
                .orElseGet(() -> {
                    StaffUser created = staff.save(new StaffUser(phone, name, clock.instant()));
                    return ResponseEntity.status(HttpStatus.CREATED).body(new BootstrapAdminResponse(created.getId(), true));
                });
    }

    /** A sign-in link for a father or a staff user, returned (never sent). 404 when that phone may not sign in. */
    @PostMapping("/login-links")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginLinkResponse loginLink(@Valid @RequestBody LoginLinkRequest request) {
        LoginLinkService.IssuedLink link = links.issueFor(request.phone()).orElseThrow(WebException::notFound);
        return new LoginLinkResponse(link.url(), link.expiresAt());
    }
}
