package com.scivicslab.coderaptor.resource;

import com.scivicslab.coderaptor.chat.ChatService;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;
import java.util.Map;

/**
 * AI Chat: questions about the code, answered by an LLM on the GPU broker that can run
 * code-raptor's symbol and Javadoc searches. The server keeps no conversation.
 *
 * <pre>
 *   GET  /api/chat/models → {"models":["qwen3.8-flash-next", …]}
 *   POST /api/chat        {"model":…, "history":[{"role":"user","content":…}, …], "question":…}
 *                         → server-sent events, one JSON object each (see ChatService)
 * </pre>
 */
@Path("/api/chat")
@ApplicationScoped
public class ChatResource {

    @Inject
    ChatService chatService;

    /** What the browser sends for one question. */
    public record Question(String model, List<Map<String, String>> history, String question) {}

    @GET
    @Path("models")
    @Produces(MediaType.APPLICATION_JSON)
    public Response models() {
        try {
            return Response.ok(chatService.models()).build();
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "Cannot reach the GPU broker: " + e.getMessage())).build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<Map<String, Object>> ask(Question q) {
        if (q == null || q.question() == null || q.question().isBlank() || q.model() == null) {
            return Multi.createFrom().item(Map.of("type", "error", "message", "model and question are required"));
        }
        List<Map<String, String>> history = q.history() == null ? List.of() : q.history().stream()
                .filter(m -> ("user".equals(m.get("role")) || "assistant".equals(m.get("role")))
                        && m.get("content") != null)
                .map(m -> Map.of("role", m.get("role"), "content", m.get("content")))
                .toList();
        return Multi.createFrom().emitter(out -> chatService.ask(q.model(), history, q.question(), out));
    }
}
