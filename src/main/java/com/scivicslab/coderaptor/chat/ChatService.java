package com.scivicslab.coderaptor.chat;

import com.scivicslab.coderaptor.index.JavadocIndexer;
import com.scivicslab.coderaptor.model.JavadocHit;
import com.scivicslab.coderaptor.model.SymbolHit;
import com.scivicslab.coderaptor.resource.SymbolResource;
import com.scivicslab.pojoactor.core.ActorRef;
import com.scivicslab.pojoactor.core.ActorSystem;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs AI Chat questions. Each question is one {@link CodeChat} actor that streams what happens
 * to the browser as JSON events and is removed when the answer is done:
 *
 * <ul>
 *   <li>{@code {"type":"text","text":…}} — a piece of the LLM's reply</li>
 *   <li>{@code {"type":"search","tool":…,"searchType":…,"query":…,"hits":…,"lines":[…]}} — a search was run</li>
 *   <li>{@code {"type":"step"}} — the reply so far asked for searches; a new reply starts</li>
 *   <li>{@code {"type":"answer","text":…}} — the answer, without search tags; the stream ends</li>
 *   <li>{@code {"type":"error","message":…}} — the stream ends</li>
 * </ul>
 */
@ApplicationScoped
public class ChatService {

    private static final Logger LOG = Logger.getLogger(ChatService.class);

    @ConfigProperty(name = "code.raptor.chat.broker-url", defaultValue = "http://192.168.5.21:30805")
    String brokerUrl;

    @ConfigProperty(name = "code.raptor.chat.model", defaultValue = "qwen3.8-flash-next")
    String defaultModel;

    @Inject SymbolResource symbols;
    @Inject JavadocIndexer javadoc;

    private ActorSystem system;
    private BrokerChat broker;
    private final AtomicInteger seq = new AtomicInteger();

    @PostConstruct
    void init() {
        system = new ActorSystem("chat-system");
        broker = new BrokerChat(brokerUrl);
    }

    @PreDestroy
    void destroy() {
        system.terminate();
    }

    /** The broker's models, with the configured default first when the broker has it. */
    public Map<String, Object> models() throws Exception {
        List<String> models = new ArrayList<>(broker.models());
        if (models.remove(defaultModel)) models.addFirst(defaultModel);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("models", models);
        return out;
    }

    /** Starts answering; events go to {@code out}, and a cancelled emitter stops the work. */
    public void ask(String model, List<Map<String, String>> history, String question,
                    MultiEmitter<? super Map<String, Object>> out) {
        String name = "chat-" + seq.incrementAndGet();
        ActorRef<CodeChat> ref = system.actorOf(name, new CodeChat(broker, this::search));
        ref.tell(chat -> {
            try {
                String answer = chat.answer(model, history, question, new CodeChat.Listener() {
                    @Override public void text(String piece) { out.emit(Map.of("type", "text", "text", piece)); }
                    @Override public void searched(SearchRequest r, int hits, List<String> lines) {
                        out.emit(Map.of("type", "search", "tool", r.tool(), "searchType", r.type(),
                                "query", r.query(), "hits", hits, "lines", lines));
                    }
                    @Override public void nextStep() { out.emit(Map.of("type", "step")); }
                }, out::isCancelled);
                out.emit(Map.of("type", "answer", "text", answer));
            } catch (Exception e) {
                LOG.errorf(e, "Chat %s failed", name);
                out.emit(Map.of("type", "error", "message", String.valueOf(e.getMessage())));
            } finally {
                out.complete();
            }
        }).whenComplete((v, e) -> system.removeActor(name));
    }

    private CodeChat.SearchResult search(SearchRequest r) {
        if (SearchRequest.JAVADOC.equals(r.tool())) {
            List<JavadocHit> hits = javadoc.search(r.query());
            return new CodeChat.SearchResult(hits.size(), hits.stream()
                    .map(h -> h.title() + " — " + h.url()).toList());
        }
        try {
            List<SymbolHit> hits = symbols.hits(r.query(), r.type());
            return new CodeChat.SearchResult(hits.size(), hits.stream()
                    .map(h -> h.file() + ":" + h.line() + ": " + h.content().strip()).toList());
        } catch (RuntimeException e) {
            return new CodeChat.SearchResult(0, List.of("(search failed: " + e.getMessage() + ")"));
        }
    }
}
