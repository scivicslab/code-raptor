package com.scivicslab.coderaptor.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Answers one question about the code in works-dir. The LLM may ask for code-raptor's symbol and
 * Javadoc searches by writing tags ({@link SearchRequest}); each asked-for search is run and its
 * result handed back, until the LLM answers without a tag or {@value #MAX_STEPS} replies were made.
 *
 * <p>Holds no conversation: the browser sends the earlier questions and answers each time.
 */
public class CodeChat {

    static final int MAX_STEPS = 6;

    static final String SYSTEM_PROMPT = """
            You answer questions about the source code of the projects in the user's works directory.
            You can search that code. To search, write one or more of these tags in your reply and stop;
            the results come back in the next message.

            <symbol_search type="definition">NAME</symbol_search>   where a class, method or field is defined
            <symbol_search type="references">NAME</symbol_search>   where it is used
            <symbol_search type="grep">TEXT</symbol_search>         lines containing the text
            <symbol_search type="files">PART</symbol_search>        files whose path contains the text
            <javadoc_search>WORDS</javadoc_search>                  Javadoc pages matching the words

            Every file name, line number, caller or definition you state must appear in a search result
            you received for the current question; earlier answers in the conversation are not evidence.
            If the searches find nothing, say so instead of guessing. Answer in the language of the question.
            """;

    /** Sent back when a reply about the code came without any search for the current question. */
    static final String SEARCH_FIRST = """
            You answered without searching. Every fact about the code must come from a search made for
            this question. Write the search tags you need now. If this question needs no facts about the
            code at all, reply with exactly NO_SEARCH and nothing else.
            """;

    static final String NO_SEARCH = "NO_SEARCH";

    /** What the caller shows while the answer is made. */
    public interface Listener {
        /** A piece of the LLM's reply as it arrives. */
        void text(String piece);
        /** A search was run; {@code summary} is one line per hit (at most {@value CodeChat#SHOWN_HITS}). */
        void searched(SearchRequest request, int hits, List<String> summary);
        /** The next reply starts; the text so far was a request for searches. */
        void nextStep();
    }

    /** Streams one LLM reply; {@link BrokerChat} is the one used in code-raptor. */
    public interface Llm {
        String stream(String model, List<Map<String, String>> messages,
                      java.util.function.Consumer<String> onText, BooleanSupplier cancelled) throws Exception;
    }

    /** What running a search gives: the total number of hits and one line per hit. */
    public record SearchResult(int hits, List<String> lines) {}

    static final int SHOWN_HITS = 30;

    private final Llm llm;
    private final Function<SearchRequest, SearchResult> search;

    public CodeChat(Llm llm, Function<SearchRequest, SearchResult> search) {
        this.llm = llm;
        this.search = search;
    }

    /**
     * @param history   earlier questions and answers, oldest first, as role/content pairs
     * @param question  the new question
     * @param cancelled true once the reader stopped waiting
     * @return the answer, with search tags removed
     */
    public String answer(String model, List<Map<String, String>> history, String question,
                         Listener listener, BooleanSupplier cancelled) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.addAll(history);
        messages.add(Map.of("role", "user", "content", question));
        boolean searched = false;
        String unsearchedReply = null;
        for (int step = 1; step <= MAX_STEPS; step++) {
            String reply = llm.stream(model, messages, listener::text, cancelled);
            if (cancelled.getAsBoolean()) return SearchRequest.withoutTags(reply);
            List<SearchRequest> requests = SearchRequest.parse(reply);
            if (requests.isEmpty()) {
                if (unsearchedReply != null && reply.strip().equals(NO_SEARCH)) return unsearchedReply;
                if (searched || unsearchedReply != null || step == MAX_STEPS) return SearchRequest.withoutTags(reply);
                // The model answered from memory; the facts have to come from a search instead.
                unsearchedReply = SearchRequest.withoutTags(reply);
                messages.add(Map.of("role", "assistant", "content", reply));
                messages.add(Map.of("role", "user", "content", SEARCH_FIRST));
                listener.nextStep();
                continue;
            }
            searched = true;
            if (step == MAX_STEPS) {
                return SearchRequest.withoutTags(reply)
                        + "\n\n(Stopped after " + MAX_STEPS + " replies that still asked for searches.)";
            }
            StringBuilder results = new StringBuilder("Search results:\n");
            for (SearchRequest r : requests) {
                SearchResult res = search.apply(r);
                List<String> shown = res.lines().subList(0, Math.min(SHOWN_HITS, res.lines().size()));
                listener.searched(r, res.hits(), shown);
                results.append("\n").append(describe(r)).append(" — ").append(res.hits()).append(" hit(s)");
                if (res.hits() > shown.size()) results.append(", first ").append(shown.size()).append(" shown");
                results.append(":\n");
                for (String l : shown) results.append(l).append('\n');
            }
            messages.add(Map.of("role", "assistant", "content", reply));
            messages.add(Map.of("role", "user", "content", results.toString()));
            listener.nextStep();
        }
        return "";
    }

    static String describe(SearchRequest r) {
        return SearchRequest.SYMBOL.equals(r.tool())
                ? "symbol_search type=" + r.type() + " \"" + r.query() + "\""
                : "javadoc_search \"" + r.query() + "\"";
    }
}
