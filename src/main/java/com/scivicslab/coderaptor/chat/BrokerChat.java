package com.scivicslab.coderaptor.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Talks to the GPU broker's OpenAI-compatible endpoints: lists the models and streams one chat
 * completion. Only plain text goes back and forth; searches are asked for with tags in the text
 * ({@link SearchRequest}), not with the API's {@code tools}.
 */
public class BrokerChat implements CodeChat.Llm {

    private static final ObjectMapper JSON = new ObjectMapper();

    // HTTP/1.1: the JDK client's h2c upgrade breaks some Python servers behind the broker.
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String baseUrl;

    public BrokerChat(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** The model ids {@code GET /v1/models} returns. */
    public List<String> models() throws IOException, InterruptedException {
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/models"))
                .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IOException("GET /v1/models returned HTTP " + res.statusCode());
        List<String> ids = new ArrayList<>();
        for (JsonNode m : JSON.readTree(res.body()).path("data")) ids.add(m.path("id").asText());
        return ids;
    }

    /**
     * Sends the messages and passes each piece of the reply to {@code onText} as it arrives.
     * Reading stops early when {@code cancelled} turns true.
     *
     * @param messages role/content pairs, system message first
     * @return the whole reply
     */
    @Override
    public String stream(String model, List<Map<String, String>> messages,
                         Consumer<String> onText, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        ObjectNode req = JSON.createObjectNode();
        req.put("model", model);
        req.put("stream", true);
        req.put("temperature", 0.3);
        req.put("max_tokens", 4096);
        // A thinking model would spend its output on reasoning the reader does not see here.
        req.putObject("chat_template_kwargs").put("enable_thinking", false);
        var arr = req.putArray("messages");
        for (Map<String, String> m : messages) {
            arr.addObject().put("role", m.get("role")).put("content", m.get("content"));
        }
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/chat/completions"))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .header("X-Job-Priority", "foreground")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(req)))
                .build();
        HttpResponse<InputStream> res = http.send(httpReq, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = res.body();
             BufferedReader br = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            if (res.statusCode() != 200) {
                throw new IOException("POST /v1/chat/completions returned HTTP " + res.statusCode()
                        + ": " + new String(body.readAllBytes(), StandardCharsets.UTF_8));
            }
            StringBuilder whole = new StringBuilder();
            String line;
            while (!cancelled.getAsBoolean() && (line = br.readLine()) != null) {
                String piece = contentOf(line);
                if (piece == null) {
                    if (line.strip().equals("data: [DONE]")) break;
                    continue;
                }
                whole.append(piece);
                onText.accept(piece);
            }
            return whole.toString();
        }
    }

    /** The text piece one SSE line of a streamed completion carries, or null when it carries none. */
    static String contentOf(String sseLine) {
        if (!sseLine.startsWith("data:")) return null;
        String data = sseLine.substring(5).strip();
        if (data.isEmpty() || data.equals("[DONE]")) return null;
        try {
            JsonNode content = JSON.readTree(data).path("choices").path(0).path("delta").path("content");
            return content.isTextual() && !content.asText().isEmpty() ? content.asText() : null;
        } catch (IOException e) {
            return null;
        }
    }
}
