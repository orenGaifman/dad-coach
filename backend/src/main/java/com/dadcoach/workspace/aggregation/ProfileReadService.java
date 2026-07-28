package com.dadcoach.workspace.aggregation;

import com.dadcoach.workspace.ResourceNotFoundException;
import com.dadcoach.workspace.dto.response.ProfileResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Reads and composes father profile data for the workspace profile view.
 *
 * <p>Owns NO state — reads from the FatherDataService. Computes derived fields
 * such as days_since_activation and masks the phone number for privacy.</p>
 */
@Service
public class ProfileReadService {

    private final FatherDataService fatherDataService;
    private final Clock clock;

    public ProfileReadService(FatherDataService fatherDataService) {
        this(fatherDataService, Clock.systemUTC());
    }

    public ProfileReadService(FatherDataService fatherDataService, Clock clock) {
        this.fatherDataService = fatherDataService;
        this.clock = clock;
    }

    /**
     * Retrieves the father's profile with computed fields.
     *
     * @param fatherId the father's unique identifier
     * @return the profile response DTO
     * @throws ResourceNotFoundException if the father is not found
     */
    public ProfileResponse getProfile(UUID fatherId) {
        FatherReadModel father = fatherDataService.getFather(fatherId)
                .orElseThrow(() -> new ResourceNotFoundException("father", fatherId));

        long daysSinceActivation = computeDaysSinceActivation(father.activatedAt());
        String maskedPhone = maskPhone(father.phone());

        return ProfileResponse.builder()
                .displayName(father.displayName())
                .phone(maskedPhone)
                .timezone(father.timezone())
                .coachingStyle(father.coachingStyle() != null ? father.coachingStyle().name() : null)
                .preferredCoachingTime(father.preferredCoachingTime())
                .languagePreference(father.languagePreference())
                .coachingPhase(father.coachingPhase() != null ? father.coachingPhase().name() : null)
                .daysSinceActivation(daysSinceActivation)
                .accountStatus(father.status() != null ? father.status().name() : null)
                .build();
    }

    /**
     * Computes the number of days since the father's activation.
     *
     * @param activatedAt the activation timestamp (may be null for fathers not yet activated)
     * @return days since activation, or 0 if not activated
     */
    long computeDaysSinceActivation(Instant activatedAt) {
        if (activatedAt == null) {
            return 0;
        }
        Duration duration = Duration.between(activatedAt, Instant.now(clock));
        return Math.max(0, duration.toDays());
    }

    /**
     * Masks a phone number showing only the last 4 digits.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"0541234567" → "***-***-4567"</li>
     *   <li>"+972541234567" → "***-***-4567"</li>
     *   <li>null → null</li>
     *   <li>"123" → "***-***-123" (short numbers kept as-is in suffix)</li>
     * </ul>
     *
     * @param phone the raw phone number
     * @return the masked phone string, or null if phone is null
     */
    String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        // Strip non-digit characters for processing
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) {
            return "***-***-" + digits;
        }
        String lastFour = digits.substring(digits.length() - 4);
        return "***-***-" + lastFour;
    }
}
