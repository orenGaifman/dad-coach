package com.dadcoach.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * One JDK HttpServer standing in for everything Dad Coach calls (no @MockBean, playbook §50): the AI Workflow
 * Platform (turns, outbound recording, tenancy person lifecycle) and Meta's Graph API (sends). Every request is
 * recorded; the platform's turn answer is programmable per test.
 */
public final class FakeServers {

    public record Call(String method, String path, Map<String, List<String>> headers, String body) {
        public String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .findFirst().map(e -> e.getValue().get(0)).orElse(null);
        }
    }

    public record Reply(int status, String body) {
        public static Reply json(String body) {
            return new Reply(200, body);
        }
    }

    public static final FakeServers INSTANCE = new FakeServers();

    private final HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final AtomicInteger sent = new AtomicInteger();
    private final AtomicReference<Function<Call, Reply>> turn = new AtomicReference<>();
    private final AtomicReference<Function<Call, Reply>> tenancy = new AtomicReference<>();
    private final AtomicReference<Function<Call, Reply>> meta = new AtomicReference<>();

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
        meta.set(c -> Reply.json("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.out." + sent.incrementAndGet() + "\"}]}"));
        turn.set(c -> Reply.json(turnReply("שלום! מה שלומך?", "GENERATED")));
        tenancy.set(c -> c.method().equals("DELETE")
                ? Reply.json("{\"outcome\":\"DELETED\",\"workflowInstances\":1,\"messages\":3}")
                : Reply.json("{\"created\":1,\"conflicts\":[]}"));
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
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Call call = new Call(ex.getRequestMethod(), ex.getRequestURI().getPath(), Map.copyOf(ex.getRequestHeaders()), body);
        calls.add(call);
        Reply reply;
        String path = call.path();
        if (path.equals("/api/v1/worker/execute")) {
            reply = turn.get().apply(call);
        } else if (path.equals("/api/v1/worker/messages/outbound")) {
            reply = Reply.json("{\"recorded\":true}");
        } else if (path.startsWith("/api/v1/tenancy/")) {
            reply = tenancy.get().apply(call);
        } else if (path.endsWith("/messages")) {
            reply = meta.get().apply(call);
        } else {
            reply = new Reply(404, "{}");
        }
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(reply.status(), bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
