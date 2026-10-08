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
 * Platform (turns, outbound recording, tenancy person lifecycle, session timers - D-037), Meta's Graph API (sends, voice-note media) and
 * ElevenLabs speech to text (D-029) and Google (token endpoint and Calendar API under {@code /google/}). Every
 * request is recorded; the platform's turn answer, the media, the transcript and Google are programmable per test.
 */
public final class FakeServers {

    public record Call(String method, String path, Map<String, List<String>> headers, String body) {
        public String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .findFirst().map(e -> e.getValue().get(0)).orElse(null);
        }
    }

    public record Reply(int status, String body, String contentType, byte[] raw) {
        public Reply(int status, String body) {
            this(status, body, "application/json", null);
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
    private final AtomicReference<Function<Call, Reply>> meta = new AtomicReference<>();
    private final AtomicReference<Function<Call, Reply>> metaTemplates = new AtomicReference<>();
    private final AtomicReference<Function<Call, Reply>> gate = new AtomicReference<>();
    private final AtomicReference<Function<Call, Reply>> google = new AtomicReference<>();
    /** null: the in-memory platform below answers {@code /api/v1/worker/scheduled-transitions}. */
    private final AtomicReference<Function<Call, Reply>> scheduledTransitions = new AtomicReference<>();
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
        metaTemplates.set(null);
        calls.clear();
        timers.clear();
        scheduledTransitions.set(null);
        meta.set(c -> Reply.json("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.out." + sent.incrementAndGet() + "\"}]}"));
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
        gate.set(c -> Reply.json("{\"send\":true,\"route\":\"dad-coach\",\"why\":\"ON_THIS_PRODUCT\"}"));
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

    /** The shared number's gate ({@code POST /api/v1/worker/whatsapp/outbound-gate}; default: send). */
    public void onGate(Function<Call, Reply> answer) {
        gate.set(answer);
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

    /** Meta's {@code GET /<version>/<waba>/message_templates} (default: no templates). */
    public void onMetaTemplates(Function<Call, Reply> answer) {
        metaTemplates.set(answer);
    }

    /** How Meta's Graph API answers a send (default: accepted with a new wamid). */
    public void onMetaSend(Function<Call, Reply> answer) {
        meta.set(answer);
    }

    public List<Call> calls(String pathPrefix) {
        return calls.stream().filter(c -> c.path().startsWith(pathPrefix)).toList();
    }

    public List<Call> turns() {
        return calls("/api/v1/worker/execute");
    }

    public List<Call> metaSends() {
        return calls.stream().filter(c -> c.path().endsWith("/messages") && !c.path().startsWith("/api/")).toList();
    }

    public List<Call> recordedOutbound() {
        return calls("/api/v1/worker/messages/outbound");
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
        } else if (path.equals("/api/v1/worker/whatsapp/outbound-gate")) {
            reply = gate.get().apply(call);
        } else if (path.equals("/api/v1/worker/scheduled-transitions")) {
            Function<Call, Reply> override = scheduledTransitions.get();
            reply = override != null ? override.apply(call) : platformTimers(call, query(ex));
        } else if (path.equals("/api/v1/worker/messages/outbound")) {
            reply = Reply.json("{\"recorded\":true}");
        } else if (path.startsWith("/api/v1/tenancy/")) {
            reply = tenancy.get().apply(call);
        } else if (path.equals("/v1/speech-to-text")) {
            reply = speechToText.get().apply(call);
        } else if (call.method().equals("GET") && path.endsWith("/message_templates")) {
            Function<Call, Reply> answer = metaTemplates.get();
            reply = answer != null ? answer.apply(call) : Reply.json("{\"data\":[]}");
        } else if (call.method().equals("GET") && (path.startsWith("/media-files/") || path.matches("/v[0-9.]+/[^/]+"))) {
            reply = media.get().apply(call);
        } else if (path.endsWith("/messages")) {
            reply = meta.get().apply(call);
        } else {
            reply = new Reply(404, "{}");
        }
        byte[] bytes = reply.raw() != null ? reply.raw() : reply.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", reply.contentType());
        ex.sendResponseHeaders(reply.status(), bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static boolean isMultipart(HttpExchange ex) {
        String type = ex.getRequestHeaders().getFirst("Content-Type");
        return type != null && type.startsWith("multipart/");
    }
}
