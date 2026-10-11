package com.dadcoach.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * One JDK HttpServer standing in for everything Dad Coach calls (no @MockBean, playbook §50): the AI Workflow
 * Platform (turns, outbound recording, tenancy person lifecycle, session timers - D-037, the Send API - D-053: the only
 * sender), Meta's Graph API (voice-note media only; a send there is recorded so the test base can prove none happens)
 * and ElevenLabs speech to text (D-029) and Google (token endpoint and Calendar API under {@code /google/}). Every
 * request is recorded; the platform's turn answer, the media, the transcript and Google are programmable per test.
 */
public final class FakeServers {

    public record Call(String method, String path, Map<String, List<String>> headers, String body) {
        public String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .findFirst().map(e -> e.getValue().get(0)).orElse(null);
        }
    }

    public record Reply(int status, String body, String contentType, byte[] raw, Map<String, String> headers) {
        public Reply(int status, String body, String contentType, byte[] raw) {
            this(status, body, contentType, raw, Map.of());
        }

        public Reply(int status, String body) {
            this(status, body, "application/json", null);
        }

        /** D-048: the same answer with a {@code Retry-After} header. */
        public Reply withRetryAfter(int seconds) {
            return new Reply(status, body, contentType, raw, Map.of("Retry-After", String.valueOf(seconds)));
        }

        public static Reply json(String body) {
            return new Reply(200, body);
        }

        public static Reply bytes(byte[] raw, String contentType) {
            return new Reply(200, "", contentType, raw);
        }
    }

    /**
     * A PENDING trigger of the fake platform's scheduled transitions (D-037): who, which timer, when, for which session,
     * and who armed it ({@code PRODUCT} through the worker API, {@code AI_TOOL} when a test plays the model arming it).
     */
    public record Timer(String triggerId, String userId, String transitionKey, Instant scheduledAt, String referenceType,
                        String referenceId, String source) {
    }

    /** The Dad Coach 3 transition keys of ACTIVE_COACHING: the fake platform arms these and refuses others (409). */
    static final java.util.Set<String> TIMER_KEYS = java.util.Set.of("session_morning_reminder", "session_reminder_1h",
            "session_follow_up");

    public static final FakeServers INSTANCE = new FakeServers();

    private final HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final AtomicInteger sent = new AtomicInteger();
    private final AtomicReference<Function<Call, Reply>> turn = new AtomicReference<>();
    private final AtomicReference<Function<Call, Reply>> tenancy = new AtomicReference<>();
    private final AtomicReference<Function<Call, Reply>> media = new AtomicReference<>();
    private final AtomicReference<Function<Call, Reply>> speechToText = new AtomicReference<>();
    private final AtomicReference<Function<Call, Reply>> google = new AtomicReference<>();
    /** null: the in-memory platform below answers {@code /api/v1/worker/scheduled-transitions}. */
    private final AtomicReference<Function<Call, Reply>> scheduledTransitions = new AtomicReference<>();
    /** null: the D-039 timeline reports (/messages/inbound, /messages/turn-outcome) are accepted. */
    private final AtomicReference<Function<Call, Reply>> timelineReports = new AtomicReference<>();
    /**
     * D-045: the platform's Send API ({@code POST /api/v1/worker/messages/send}); default (D-053) = the platform takes
     * it: ACCEPTED with a wamid, the same key replayed ({@link #accept}).
     */
    private final AtomicReference<Function<Call, Reply>> platformSend = new AtomicReference<>();
    /** D-053: {@code GET /api/v1/worker/messaging/kinds} and {@code /whatsapp/templates}; default = the seed, all usable. */
    private final AtomicReference<Function<Call, Reply>> platformTemplates = new AtomicReference<>();
    /** What the default Send API accepted, by idempotency key: its message id (a re-POST replays it). */
    private final Map<String, String> accepted = new java.util.concurrent.ConcurrentHashMap<>();
    /** D-045: {@code GET /api/v1/worker/messages?workerKey&idempotencyKey}; default = 404 not found. */
    private final AtomicReference<Function<Call, Reply>> platformLookup = new AtomicReference<>();
    private final List<Map<String, String>> lookupQueries = new CopyOnWriteArrayList<>();
    private final List<Timer> timers = new CopyOnWriteArrayList<>();
    private final AtomicInteger triggerIds = new AtomicInteger();
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private FakeServers() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        reset();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void reset() {
        calls.clear();
        accepted.clear();
        platformTemplates.set(null);
        timers.clear();
        scheduledTransitions.set(null);
        timelineReports.set(null);
        lookupQueries.clear();
        platformSend.set(this::accept);
        platformLookup.set(c -> new Reply(404, "{\"error\":\"messaging.message_not_found\",\"message\":\"no such message\"}"));
        turn.set(c -> Reply.json(turnReply("שלום! מה שלומך?", "GENERATED")));
        tenancy.set(c -> c.method().equals("DELETE")
                ? Reply.json("{\"outcome\":\"DELETED\",\"workflowInstances\":1,\"messages\":3}")
                : Reply.json("{\"created\":1,\"conflicts\":[]}"));
        // Meta media (D-029): GET /<version>/<media-id> names the file, GET /media-files/<media-id> is the file
        media.set(c -> c.path().startsWith("/media-files/")
                ? Reply.bytes(new byte[] {79, 103, 103, 83, 1, 2, 3}, "audio/ogg")
                : Reply.json("{\"url\":\"" + baseUrl() + "/media-files/" + c.path().substring(c.path().lastIndexOf('/') + 1)
                        + "\",\"mime_type\":\"audio/ogg; codecs=opus\",\"file_size\":7,\"id\":\"media\"}"));
        speechToText.set(c -> Reply.json("{\"language_code\":\"heb\",\"text\":\"רוצה לקבוע זמן עם נועה ביום שישי\"}"));
        // Google: tokens are granted, the calendar is empty, a new event gets an id
        google.set(c -> c.path().equals("/google/token")
                ? Reply.json("{\"access_token\":\"fresh-access\",\"refresh_token\":\"fresh-refresh\",\"expires_in\":3600}")
                : c.method().equals("POST") ? Reply.json("{\"id\":\"evt-" + sent.incrementAndGet() + "\"}")
                : Reply.json("{\"items\":[]}"));
    }

    /** Google: {@code /google/token} and the Calendar API under {@code /google/calendar/v3}. */
    public void onGoogle(Function<Call, Reply> answer) {
        google.set(answer);
    }

    public List<Call> googleEventCreates() {
        return calls.stream().filter(c -> c.method().equals("POST") && c.path().startsWith("/google/calendar/")).toList();
    }

    /** D-045: how the platform's Send API answers ({@code POST /api/v1/worker/messages/send}). */
    public void onPlatformSend(Function<Call, Reply> answer) {
        platformSend.set(answer);
    }

    /** D-045: how the platform answers a lookup by key ({@code GET /api/v1/worker/messages?...}). */
    public void onPlatformLookup(Function<Call, Reply> answer) {
        platformLookup.set(answer);
    }

    public List<Call> platformSends() {
        return calls.stream().filter(c -> c.path().equals("/api/v1/worker/messages/send")).toList();
    }

    /** D-053: every {@code /send} request body, parsed, in order. */
    public List<com.fasterxml.jackson.databind.JsonNode> sendRequests() {
        return platformSends().stream().map(c -> {
            try {
                return JSON.readTree(c.body());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }).toList();
    }

    /** D-053: the {@code /send} requests of one kind (LOGIN_LINK, SCHEDULED, REPLY, FIXED_LINE, ...). */
    public List<com.fasterxml.jackson.databind.JsonNode> sendRequests(String kind) {
        return sendRequests().stream().filter(r -> kind.equals(r.path("kind").asText())).toList();
    }

    /**
     * D-053: what a father reads, request by request: the free-form {@code content.text} of each distinct message
     * (a re-POST of the same key counted once), in order - what Meta's sends used to show.
     */
    public List<String> sentTexts() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        return sendRequests().stream().filter(r -> keys.add(r.path("idempotencyKey").asText()))
                .map(r -> r.path("content").path("text").asText(null)).toList();
    }

    /** D-053: the distinct messages handed to the platform (re-POSTs of a key counted once), in order. */
    public List<com.fasterxml.jackson.databind.JsonNode> sentMessages() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        return sendRequests().stream().filter(r -> keys.add(r.path("idempotencyKey").asText())).toList();
    }

    /**
     * D-053: the default Send API - the platform takes the message: 200 ACCEPTED with a new wamid, channel window
     * SESSION when it carries content, else TEMPLATE; the same key again replays the same message ({@code replayed}).
     */
    public Reply accept(Call c) {
        String key;
        boolean content;
        try {
            com.fasterxml.jackson.databind.JsonNode r = JSON.readTree(c.body());
            key = r.path("idempotencyKey").asText();
            content = r.hasNonNull("content");
        } catch (IOException e) {
            return new Reply(400, "{\"error\":\"messaging.invalid_request\"}");
        }
        String existing = accepted.get(key);
        String messageId = existing != null ? existing
                : java.util.UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        String wamid = "wamid.platform." + messageId.substring(0, 8);
        accepted.put(key, messageId);
        return Reply.json("{\"messageId\":\"" + messageId + "\",\"status\":\"ACCEPTED\",\"providerMessageId\":\""
                + wamid + "\",\"channelWindow\":\"" + (content ? "SESSION" : "TEMPLATE") + "\",\"replayed\":"
                + (existing != null) + "}");
    }

    /** The platform's word on a key, as its answers carry it ({@code status} QUEUED / HELD / FAILED / ...). */
    public static Reply platformAnswer(int httpStatus, String messageId, String status, String reason, String wamid) {
        return new Reply(httpStatus, "{\"messageId\":\"" + messageId + "\",\"status\":\"" + status + "\""
                + (reason == null ? "" : ",\"reason\":\"" + reason + "\"")
                + (wamid == null ? "" : ",\"providerMessageId\":\"" + wamid + "\"") + "}");
    }

    /** The kill switch's answer (spec v2.5: nothing was created). */
    public static Reply platformDisabled() {
        return new Reply(503, "{\"error\":\"messaging.disabled\",\"reason\":\"DISABLED\","
                + "\"message\":\"the platform does not send this message now (nothing was created)\"}");
    }

    /** D-053: how the platform answers {@code /messaging/kinds} and {@code /whatsapp/templates} (null = the default). */
    public void onPlatformTemplates(Function<Call, Reply> answer) {
        platformTemplates.set(answer);
    }

    /** The query of every lookup by key, in order. */
    public List<Map<String, String>> platformLookups() {
        return List.copyOf(lookupQueries);
    }

    // ---- the platform's scheduled transitions (D-037) ---------------------------------------------------------------

    /** Overrides the in-memory platform for {@code /api/v1/worker/scheduled-transitions} (e.g. down, or refusing). */
    public void onScheduledTransitions(Function<Call, Reply> answer) {
        scheduledTransitions.set(answer);
    }

    /** The timers the fake platform holds as PENDING. */
    public List<Timer> pendingTimers() {
        return List.copyOf(timers);
    }

    /** Plays the model arming a timer in its turn (schedule_state_transition): same dedupe as the platform. */
    public Timer armAsModel(String userId, String transitionKey, Instant at, String referenceId) {
        return arm(userId, transitionKey, at, "quality_time", referenceId, "AI_TOOL");
    }

    public List<Call> scheduledTransitionCalls() {
        return calls("/api/v1/worker/scheduled-transitions");
    }

    public List<Call> scheduledTransitionCalls(String method) {
        return scheduledTransitionCalls().stream().filter(c -> c.method().equals(method)).toList();
    }

    private Timer arm(String userId, String key, Instant at, String refType, String refId, String source) {
        for (Timer t : timers) {
            if (t.userId().equals(userId) && t.transitionKey().equals(key) && refType.equals(t.referenceType())
                    && refId.equals(t.referenceId())) {
                Timer moved = new Timer(t.triggerId(), userId, key, at, refType, refId, t.source());
                timers.set(timers.indexOf(t), moved);
                return moved;
            }
        }
        Timer created = new Timer("00000000-0000-0000-0000-" + String.format("%012d", triggerIds.incrementAndGet()), userId,
                key, at, refType, refId, source);
        timers.add(created);
        return created;
    }

    /** The platform's worker API for timers, in memory: GET pending, POST arm (409 off-state keys), DELETE by reference. */
    private Reply platformTimers(Call call, Map<String, String> query) {
        try {
            switch (call.method()) {
                case "GET" -> {
                    List<Map<String, Object>> pending = new java.util.ArrayList<>();
                    timers.stream().filter(t -> t.userId().equals(query.get("userId")))
                            .sorted(java.util.Comparator.comparing(Timer::scheduledAt)).forEach(t -> {
                                Map<String, Object> m = new java.util.LinkedHashMap<>();
                                m.put("triggerId", t.triggerId());
                                m.put("transitionKey", t.transitionKey());
                                m.put("targetStateKey", t.transitionKey().toUpperCase());
                                m.put("scheduledAt", t.scheduledAt().toString());
                                m.put("referenceType", t.referenceType());
                                m.put("referenceId", t.referenceId());
                                m.put("source", t.source());
                                pending.add(m);
                            });
                    return Reply.json(JSON.writeValueAsString(Map.of("instanceId", "11111111-1111-1111-1111-111111111111",
                            "currentStateKey", "ACTIVE_COACHING", "pending", pending)));
                }
                case "POST" -> {
                    var body = JSON.readTree(call.body());
                    String key = body.path("transitionKey").asText();
                    if (!TIMER_KEYS.contains(key)) {
                        return new Reply(409, "{\"error\":\"TRANSITION_NOT_ON_CURRENT_STATE\",\"currentStateKey\":\"ACTIVE_COACHING\"}");
                    }
                    Timer t = arm(body.path("userId").asText(), key, Instant.parse(body.path("scheduledAt").asText()),
                            body.path("referenceType").asText(), body.path("referenceId").asText(), "PRODUCT");
                    return Reply.json(JSON.writeValueAsString(Map.of("triggerId", t.triggerId(), "transitionKey", key,
                            "targetStateKey", key.toUpperCase(), "scheduledAt", t.scheduledAt().toString(),
                            "referenceType", t.referenceType(), "referenceId", t.referenceId())));
                }
                case "DELETE" -> {
                    List<Timer> gone = timers.stream().filter(t -> t.userId().equals(query.get("userId"))
                            && t.referenceId().equals(query.get("referenceId"))
                            && (query.get("transitionKey") == null || t.transitionKey().equals(query.get("transitionKey")))).toList();
                    timers.removeAll(gone);
                    return Reply.json("{\"cancelled\":" + gone.size() + ",\"triggerIds\":[]}");
                }
                default -> {
                    return new Reply(405, "{}");
                }
            }
        } catch (IOException e) {
            return new Reply(400, "{}");
        }
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> out = new java.util.HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                out.put(java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    public static String turnReply(String content, String outcome) {
        String c = content == null ? "null" : "\"" + content.replace("\"", "\\\"") + "\"";
        return "{\"instanceId\":\"11111111-1111-1111-1111-111111111111\",\"currentStateKey\":\"ACTIVE_COACHING\","
                + "\"responseContent\":" + c + ",\"responseType\":\"text\",\"metadata\":{\"responseOutcome\":\""
                + outcome + "\"},\"isDuplicate\":false}";
    }

    public void onTurn(Function<Call, Reply> answer) {
        turn.set(answer);
    }

    public void onTenancy(Function<Call, Reply> answer) {
        tenancy.set(answer);
    }

    /** Meta's media lookup ({@code GET /<version>/<id>}) and download ({@code GET /media-files/<id>}). */
    public void onMedia(Function<Call, Reply> answer) {
        media.set(answer);
    }

    /** ElevenLabs {@code POST /v1/speech-to-text}. */
    public void onSpeechToText(Function<Call, Reply> answer) {
        speechToText.set(answer);
    }

    public List<Call> speechToTextCalls() {
        return calls("/v1/speech-to-text");
    }

    public List<Call> mediaCalls() {
        return calls.stream().filter(c -> c.method().equals("GET")
                && (c.path().startsWith("/media-files/") || c.path().matches("/v[0-9.]+/[^/]+"))).toList();
    }

    public List<Call> calls(String pathPrefix) {
        return calls.stream().filter(c -> c.path().startsWith(pathPrefix)).toList();
    }

    public List<Call> turns() {
        return calls("/api/v1/worker/execute");
    }

    /** Sends to Meta's Graph API ({@code POST /<version>/<phone id>/messages}) - D-053: never (the test base checks). */
    public List<Call> metaSends() {
        return calls.stream().filter(c -> c.path().endsWith("/messages") && !c.path().startsWith("/api/")).toList();
    }

    public List<Call> recordedOutbound() {
        return calls("/api/v1/worker/messages/outbound");
    }

    // ---- D-039: the platform's timeline reports (Phase 3.4) -----------------------------------------------------------

    /** {@code POST /api/v1/worker/messages/inbound}: a father's message the product answered without a turn. */
    public List<Call> recordedInbound() {
        return calls("/api/v1/worker/messages/inbound");
    }

    /** {@code POST /api/v1/worker/messages/turn-outcome}. */
    public List<Call> turnOutcomes() {
        return calls("/api/v1/worker/messages/turn-outcome");
    }

    /** Every timeline call (inbound, outbound, turn-outcome), in the order they arrived. */
    public List<Call> timelineCalls() {
        return calls.stream().filter(c -> c.path().equals("/api/v1/worker/messages/inbound")
                || c.path().equals("/api/v1/worker/messages/outbound")
                || c.path().equals("/api/v1/worker/messages/turn-outcome")).toList();
    }

    /**
     * How the platform answers the timeline calls (inbound, outbound, turn-outcome), e.g. down or refusing. Default: the
     * platform's answers ({@code /messages/outbound} keeps its {@code {"recorded":true}}).
     */
    public void onTimelineReports(Function<Call, Reply> answer) {
        timelineReports.set(answer);
    }

    private void handle(HttpExchange ex) throws IOException {
        // ISO-8859-1 keeps a multipart body's bytes one char each (the audio inside is not text)
        byte[] in = ex.getRequestBody().readAllBytes();
        String body = new String(in, isMultipart(ex) ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8);
        Call call = new Call(ex.getRequestMethod(), ex.getRequestURI().getPath(), Map.copyOf(ex.getRequestHeaders()), body);
        calls.add(call);
        Reply reply;
        String path = call.path();
        if (path.startsWith("/google/")) {
            reply = google.get().apply(call);
        } else if (path.equals("/api/v1/worker/execute")) {
            reply = turn.get().apply(call);
        } else if (path.equals("/api/v1/worker/messages/send")) {
            reply = platformSend.get().apply(call);
        } else if (path.equals("/api/v1/worker/messages") && call.method().equals("GET")) {
            lookupQueries.add(query(ex));
            reply = platformLookup.get().apply(call);
        } else if (path.equals("/api/v1/worker/messaging/kinds") || path.equals("/api/v1/worker/whatsapp/templates")) {
            Function<Call, Reply> override = platformTemplates.get();
            reply = override != null ? override.apply(call) : defaultTemplates(path);
        } else if (path.equals("/api/v1/worker/scheduled-transitions")) {
            Function<Call, Reply> override = scheduledTransitions.get();
            Reply answered = override != null ? override.apply(call) : null;      // null: the in-memory platform answers
            reply = answered != null ? answered : platformTimers(call, query(ex));
        } else if (path.equals("/api/v1/worker/messages/outbound") || path.equals("/api/v1/worker/messages/inbound")
                || path.equals("/api/v1/worker/messages/turn-outcome")) {
            Function<Call, Reply> override = timelineReports.get();
            reply = override != null ? override.apply(call)
                    : path.endsWith("/turn-outcome") ? Reply.json("{\"applied\":true}") : Reply.json("{\"recorded\":true}");
        } else if (path.startsWith("/api/v1/tenancy/")) {
            reply = tenancy.get().apply(call);
        } else if (path.equals("/v1/speech-to-text")) {
            reply = speechToText.get().apply(call);
        } else if (call.method().equals("GET") && (path.startsWith("/media-files/") || path.matches("/v[0-9.]+/[^/]+"))) {
            reply = media.get().apply(call);
        } else if (path.endsWith("/messages")) {
            // D-053: Dad Coach never sends to Meta; recorded (metaSends) and refused
            reply = new Reply(410, "{\"error\":{\"message\":\"Dad Coach does not send to Meta (D-053)\"}}");
        } else {
            reply = new Reply(404, "{}");
        }
        byte[] bytes = reply.raw() != null ? reply.raw() : reply.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", reply.contentType());
        if (reply.headers() != null) {
            reply.headers().forEach((k, v) -> ex.getResponseHeaders().add(k, v));
        }
        ex.sendResponseHeaders(reply.status(), bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** The platform's default view of Dad Coach's templates: the dad_coach seed rows, every template approved. */
    private static Reply defaultTemplates(String path) {
        try {
            com.fasterxml.jackson.databind.JsonNode seed = JSON.readTree(FakeServers.class.getResourceAsStream(
                    "/kind-template-seed.json"));
            java.util.Map<String, java.util.List<java.util.Map<String, Object>>> byKind = new java.util.LinkedHashMap<>();
            java.util.Set<String> names = new java.util.LinkedHashSet<>();
            for (com.fasterxml.jackson.databind.JsonNode row : seed) {
                if (!"dad_coach".equals(row.path("product").asText())) {
                    continue;
                }
                String kv = row.path("kind").asText() + "|" + row.path("variant").asText();
                names.add(row.path("template").asText());
                byKind.computeIfAbsent(kv, k -> new java.util.ArrayList<>()).add(Map.of("template",
                        row.path("template").asText(), "language", "he", "enabled", true, "status", "APPROVED", "usable", true));
            }
            if (path.endsWith("/templates")) {
                List<Map<String, Object>> templates = new java.util.ArrayList<>();
                names.forEach(n -> templates.add(Map.of("name", n, "language", "he", "status", "APPROVED",
                        "category", "UTILITY")));
                return Reply.json(JSON.writeValueAsString(Map.of("senderStatus", "OK", "wabaId", "200000000000002",
                        "templates", templates)));
            }
            List<Map<String, Object>> kinds = new java.util.ArrayList<>();
            byKind.forEach((kv, candidates) -> kinds.add(Map.of("kind", kv.substring(0, kv.indexOf('|')),
                    "variant", kv.substring(kv.indexOf('|') + 1), "templateUsable", true, "candidates", candidates)));
            return Reply.json(JSON.writeValueAsString(Map.of("product", "dad_coach", "senderStatus", "OK", "snapshot", true,
                    "kinds", kinds)));
        } catch (IOException e) {
            return new Reply(500, "{}");
        }
    }

    private static boolean isMultipart(HttpExchange ex) {
        String type = ex.getRequestHeaders().getFirst("Content-Type");
        return type != null && type.startsWith("multipart/");
    }
}
