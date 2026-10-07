package com.dadcoach.web.auth;

import com.dadcoach.auth.DashboardPrincipal;
import com.dadcoach.web.training.TrainingService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    private final Factory factory;

    public MeController(Factory factory) {
        this.factory = factory;
    }

    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal DashboardPrincipal principal) {
        return factory.of(principal);
    }

    @Component
    public static class Factory {

        private final TrainingService training;

        public Factory(TrainingService training) {
            this.training = training;
        }

        public MeResponse of(DashboardPrincipal p) {
            List<String> caps = new ArrayList<>();
            if (p.isFather()) {
                caps.add("father");
                if (training.available()) {
                    caps.add("training");
                }
            }
            if (p.isAdmin()) {
                caps.add("admin");
            }
            return new MeResponse(p.isAdmin() ? "ADMIN" : "FATHER", p.displayName(), caps, p.fatherId());
        }
    }
}
