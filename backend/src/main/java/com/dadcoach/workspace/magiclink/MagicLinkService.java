package com.dadcoach.workspace.magiclink;

import com.dadcoach.domain.father.Father;
import com.dadcoach.domain.father.FatherRepository;
import com.dadcoach.onboarding.invitation.InvitationTokenGenerator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Service for creating and validating magic link tokens.
 * 
 * Magic links allow fathers to authenticate from WhatsApp without manual login.
 * The flow:
 * 1. Generate: Create a magic link token and return the full URL
 * 2. Validate: When father clicks the link, validate token and return JWT
 * 
 * Security:
 * - Tokens are single-use (consumed after successful validation)
 * - Tokens expire after 60 minutes
 * - Only one active token per father at a time
 */
@Service
public class MagicLinkService {

    private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);
    private static final Duration DEFAULT_TOKEN_EXPIRY = Duration.ofDays(7);
    private static final Duration JWT_EXPIRY = Duration.ofDays(7);

    private final MagicLinkRepository repository;
    private final FatherRepository fatherRepository;
    private final InvitationTokenGenerator tokenGenerator;
    private final Clock clock;
    private final SecretKey signingKey;
    private final String issuer;
    private final String webAppBaseUrl;

    public MagicLinkService(
            MagicLinkRepository repository,
            FatherRepository fatherRepository,
            InvitationTokenGenerator tokenGenerator,
            Clock clock,
            @Value("${dad-coach.security.jwt.secret}") String jwtSecret,
            @Value("${dad-coach.security.jwt.issuer}") String issuer,
            @Value("${dad-coach.web.base-url:http://localhost:3000}") String webAppBaseUrl) {
        this.repository = repository;
        this.fatherRepository = fatherRepository;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.webAppBaseUrl = webAppBaseUrl;
    }

    /**
     * Generates a new magic link for a father.
     * 
     * @param fatherId The father to generate the link for
     * @param redirectPath The path to redirect to after authentication (e.g., "/growth")
     * @param context Context for analytics (e.g., "quality_time_logged")
     * @return The full magic link URL
     */
    @Transactional
    public String generateMagicLink(Long fatherId, String redirectPath, String context) {
        // Verify father exists
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new IllegalArgumentException("Father not found: " + fatherId));

        // Invalidate any existing tokens for this father
        Instant now = Instant.now(clock);
        int invalidated = repository.invalidateAllForFather(fatherId, now);
        if (invalidated > 0) {
            log.debug("Invalidated {} existing magic link tokens for father {}", invalidated, fatherId);
        }

        // Generate new token
        String token = tokenGenerator.generateToken();
        Instant expiresAt = now.plus(DEFAULT_TOKEN_EXPIRY);

        MagicLink magicLink = new MagicLink(token, fatherId, expiresAt, redirectPath, context);
        repository.save(magicLink);

        log.info("Generated magic link for father {} with context '{}', expires at {}", 
                fatherId, context, expiresAt);

        // Build the full URL
        return buildMagicLinkUrl(token, redirectPath, context);
    }

    /**
     * Validates a magic link token and returns authentication credentials.
     * 
     * @param token The magic link token
     * @return Validation result with JWT on success, or error details on failure
     */
    @Transactional
    public MagicLinkValidationResult validateToken(String token) {
        Optional<MagicLink> optMagicLink = repository.findByToken(token);

        if (optMagicLink.isEmpty()) {
            log.warn("Magic link validation failed: token not found");
            return MagicLinkValidationResult.invalid("TOKEN_INVALID", 
                    "This link is invalid. Please request a new one.");
        }

        MagicLink magicLink = optMagicLink.get();

        if (magicLink.isConsumed()) {
            log.warn("Magic link validation failed: token already used for father {}", 
                    magicLink.getFatherId());
            return MagicLinkValidationResult.invalid("TOKEN_USED", 
                    "This link has already been used. Please request a new one.");
        }

        if (magicLink.isExpired()) {
            log.warn("Magic link validation failed: token expired for father {}", 
                    magicLink.getFatherId());
            return MagicLinkValidationResult.expired(
                    "This link has expired. Please request a new one from your coach.");
        }

        // Token is valid - consume it and generate JWT
        magicLink.consume();
        repository.save(magicLink);

        // Generate JWT for the father
        String jwt = generateJwt(magicLink.getFatherId());

        log.info("Magic link validated successfully for father {}, context '{}'", 
                magicLink.getFatherId(), magicLink.getContext());

        return MagicLinkValidationResult.success(jwt, magicLink.getRedirectPath());
    }

    /**
     * Gets an existing valid magic link for a father, if one exists.
     * Used to avoid generating duplicate links in quick succession.
     */
    @Transactional(readOnly = true)
    public Optional<String> getExistingValidLink(Long fatherId) {
        return repository.findValidByFatherId(fatherId, Instant.now(clock))
                .map(link -> buildMagicLinkUrl(link.getToken(), link.getRedirectPath(), link.getContext()));
    }

    /**
     * Generates a JWT access token for a father.
     */
    private String generateJwt(Long fatherId) {
        Instant now = Instant.now(clock);
        Instant expiry = now.plus(JWT_EXPIRY);

        // Load father to get phone for subject
        Father father = fatherRepository.findById(fatherId)
                .orElseThrow(() -> new IllegalStateException("Father not found: " + fatherId));

        return Jwts.builder()
                .subject(father.getPhone())
                .claim("father_id", fatherId.toString())
                .claim("role", "FATHER")
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Builds the full magic link URL with all parameters.
     */
    private String buildMagicLinkUrl(String token, String redirectPath, String context) {
        StringBuilder url = new StringBuilder(webAppBaseUrl)
                .append("/auth/magic?token=")
                .append(token);

        if (redirectPath != null && !redirectPath.isBlank()) {
            url.append("&redirect=").append(redirectPath);
        }

        // Add UTM parameters for analytics
        url.append("&utm_source=whatsapp")
           .append("&utm_medium=coach");

        if (context != null && !context.isBlank()) {
            url.append("&utm_campaign=").append(context.replace("_", "-"));
        }

        return url.toString();
    }

    /**
     * Result of magic link validation.
     */
    public record MagicLinkValidationResult(
            boolean success,
            String accessToken,
            String redirectPath,
            String errorCode,
            String errorMessage
    ) {
        public static MagicLinkValidationResult success(String accessToken, String redirectPath) {
            return new MagicLinkValidationResult(true, accessToken, redirectPath, null, null);
        }

        public static MagicLinkValidationResult invalid(String errorCode, String message) {
            return new MagicLinkValidationResult(false, null, null, errorCode, message);
        }

        public static MagicLinkValidationResult expired(String message) {
            return new MagicLinkValidationResult(false, null, null, "TOKEN_EXPIRED", message);
        }
    }
}
