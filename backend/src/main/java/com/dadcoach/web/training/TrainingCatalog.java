package com.dadcoach.web.training;

import com.dadcoach.auth.DashboardProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * The one canonical list of training videos (Big Boss D-171), read once from {@code classpath:training/catalog.json}.
 * Nothing is shown while TRAINING_MEDIA_BASE_URL is unset: the catalog ships before the videos do. Every address
 * handed out is signed for Bunny's token authentication and expires (unless TRAINING_MEDIA_UNSIGNED, a local lab).
 */
@Component
public class TrainingCatalog {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    static final Duration URL_TTL = Duration.ofHours(2);

    private final List<TrainingVideo> videos;
    private final String mediaBaseUrl;
    private final String tokenKey;
    private final boolean mediaConfigured;
    private final Clock clock;

    @Autowired
    public TrainingCatalog(ResourceLoader resources, ObjectMapper json, DashboardProperties properties, Clock clock) {
        this(read(resources, json), properties.getTraining().getMediaBaseUrl(), properties.getTraining().getMediaTokenKey(),
                properties.getTraining().isMediaUnsigned(), clock);
    }

    TrainingCatalog(List<TrainingVideo> videos, String mediaBaseUrl, String tokenKey, boolean allowUnsigned, Clock clock) {
        this.videos = validated(videos);
        String base = mediaBaseUrl == null ? "" : mediaBaseUrl.strip();
        this.mediaBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.tokenKey = tokenKey == null ? "" : tokenKey.strip();
        this.mediaConfigured = !this.mediaBaseUrl.isEmpty() && (!this.tokenKey.isEmpty() || allowUnsigned);
        this.clock = clock;
    }

    public boolean mediaConfigured() {
        return mediaConfigured;
    }

    /** Everything, inactive entries included - the team's view. */
    public List<TrainingVideo> all() {
        return videos;
    }

    /** What a father sees: active, with a file, and the media host set. */
    public List<TrainingVideo> available() {
        return mediaConfigured ? videos.stream().filter(TrainingVideo::ready).toList() : List.of();
    }

    public Optional<TrainingVideo> findAvailable(String slug) {
        return available().stream().filter(v -> v.slug().equals(slug)).findFirst();
    }

    public String mediaUrl(String path) {
        if (path == null || path.isBlank() || !mediaConfigured) {
            return null;
        }
        String absolute = path.startsWith("/") ? path : "/" + path;
        if (tokenKey.isEmpty()) {
            return mediaBaseUrl + absolute;
        }
        long expires = clock.instant().plus(URL_TTL).getEpochSecond();
        return mediaBaseUrl + absolute + "?token=" + bunnyToken(tokenKey, absolute + expires) + "&expires=" + expires;
    }

    /** Bunny advanced token: "HS256-" + base64url(HMAC-SHA256(key, path + expires)), no padding. */
    static String bunnyToken(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "HS256-" + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Training media signing failed", e);
        }
    }

    private static List<TrainingVideo> read(ResourceLoader resources, ObjectMapper json) {
        try (InputStream in = resources.getResource("classpath:training/catalog.json").getInputStream()) {
            return json.readValue(in, new TypeReference<List<TrainingVideo>>() { });
        } catch (IOException e) {
            throw new IllegalStateException("Training catalog not readable", e);
        }
    }

    /** A mistake in the catalog fails startup (and the tests), never a page. */
    static List<TrainingVideo> validated(List<TrainingVideo> videos) {
        Set<String> seen = new HashSet<>();
        int primaries = 0;
        for (TrainingVideo v : videos) {
            if (v.slug() == null || !SLUG.matcher(v.slug()).matches() || !seen.add(v.slug())) {
                throw new IllegalStateException("Training catalog: bad or duplicate slug " + v.slug());
            }
            for (String file : new String[] {v.video(), v.poster()}) {
                if (file != null && file.contains("://")) {
                    throw new IllegalStateException("Training catalog: " + v.slug() + " must name a path under the media host");
                }
            }
            if (v.title() == null || v.title().isBlank() || v.durationSeconds() <= 0) {
                throw new IllegalStateException("Training catalog: " + v.slug() + " needs a title and a duration");
            }
            primaries += v.primary() ? 1 : 0;
        }
        if (primaries > 1) {
            throw new IllegalStateException("Training catalog: more than one primary video");
        }
        List<TrainingVideo> sorted = new ArrayList<>(videos);
        sorted.sort(Comparator.comparingInt(TrainingVideo::order).thenComparing(TrainingVideo::slug));
        return List.copyOf(sorted);
    }
}
