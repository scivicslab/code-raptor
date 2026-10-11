package com.scivicslab.coderaptor.chat;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The AI Chat turn: tags in the reply become searches, their results go back, a tag-free reply is the answer. */
class CodeChatTest {

    @Test
    void tagsAreReadWithTheirSearchType() {
        List<SearchRequest> r = SearchRequest.parse("""
                Let me look.
                <symbol_search type="references">requestFromWorker</symbol_search>
                <javadoc_search> ActorRef tell </javadoc_search>
                <symbol_search type="nonsense">Foo</symbol_search>
                <symbol_search>   </symbol_search>""");
        assertEquals(List.of(
                new SearchRequest("symbol_search", "references", "requestFromWorker"),
                new SearchRequest("javadoc_search", "", "ActorRef tell"),
                new SearchRequest("symbol_search", "definition", "Foo")), r);
        assertEquals("Let me look.", SearchRequest.withoutTags("Let me look.\n<javadoc_search>x</javadoc_search>"));
    }

    @Test
    void streamedLinesGiveOnlyTheirText() {
        assertEquals("Hel", BrokerChat.contentOf("data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}"));
        assertNull(BrokerChat.contentOf("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}"));
        assertNull(BrokerChat.contentOf("data: [DONE]"));
        assertNull(BrokerChat.contentOf(": ping"));
    }

    @Test
    void searchResultsGoBackUntilTheReplyHasNoTag() throws Exception {
        Deque<String> replies = new ArrayDeque<>(List.of(
                "<symbol_search type=\"definition\">requestFromWorker</symbol_search>",
                "It is defined in ChatSession.java at line 1854."));
        List<List<Map<String, String>>> sent = new ArrayList<>();
        CodeChat.Llm llm = (model, messages, onText, cancelled) -> {
            sent.add(List.copyOf(messages));
            String reply = replies.removeFirst();
            onText.accept(reply);
            return reply;
        };
        List<String> searched = new ArrayList<>();
        CodeChat chat = new CodeChat(llm, r -> new CodeChat.SearchResult(1,
                List.of("app/ChatSession.java:1854: public ActionResult requestFromWorker(String instruction) {")));
        String answer = chat.answer("m", List.of(), "Where is requestFromWorker defined?", new CodeChat.Listener() {
            @Override public void text(String piece) {}
            @Override public void searched(SearchRequest r, int hits, List<String> summary) { searched.add(r.query() + ":" + hits); }
            @Override public void nextStep() {}
        }, () -> false);

        assertEquals("It is defined in ChatSession.java at line 1854.", answer);
        assertEquals(List.of("requestFromWorker:1"), searched);
        assertEquals(2, sent.size());
        String results = sent.get(1).getLast().get("content");
        assertTrue(results.contains("app/ChatSession.java:1854"), results);
    }

    @Test
    void anAnswerMadeWithoutSearchingIsSentBackOnce() throws Exception {
        Deque<String> replies = new ArrayDeque<>(List.of(
                "It is called from ChatSession.java line 1904.",                       // from memory
                "<symbol_search type=\"references\">requestFromWorker</symbol_search>",
                "It is called from ChatSessionIIAR.java line 339."));
        List<String> lastUserMessages = new ArrayList<>();
        CodeChat chat = new CodeChat((model, messages, onText, cancelled) -> {
            lastUserMessages.add(messages.getLast().get("content"));
            return replies.removeFirst();
        }, r -> new CodeChat.SearchResult(1, List.of("app/ChatSessionIIAR.java:339: return chatSession().requestFromWorker(args.request());")));
        String answer = chat.answer("m", List.of(), "Where is it called from?", quiet(), () -> false);
        assertEquals("It is called from ChatSessionIIAR.java line 339.", answer);
        assertEquals(CodeChat.SEARCH_FIRST, lastUserMessages.get(1));
    }

    @Test
    void aQuestionThatNeedsNoSearchKeepsTheFirstReply() throws Exception {
        Deque<String> replies = new ArrayDeque<>(List.of("Hello! Ask me about the code.", "NO_SEARCH"));
        CodeChat chat = new CodeChat((model, messages, onText, cancelled) -> replies.removeFirst(),
                r -> new CodeChat.SearchResult(0, List.of()));
        assertEquals("Hello! Ask me about the code.", chat.answer("m", List.of(), "Hi", quiet(), () -> false));
    }

    private static CodeChat.Listener quiet() {
        return new CodeChat.Listener() {
            @Override public void text(String piece) {}
            @Override public void searched(SearchRequest r, int hits, List<String> summary) {}
            @Override public void nextStep() {}
        };
    }

    @Test
    void repliesThatKeepAskingStopAfterTheLimit() throws Exception {
        CodeChat chat = new CodeChat((model, messages, onText, cancelled) -> "<javadoc_search>x</javadoc_search>",
                r -> new CodeChat.SearchResult(0, List.of()));
        String answer = chat.answer("m", List.of(), "q", new CodeChat.Listener() {
            @Override public void text(String piece) {}
            @Override public void searched(SearchRequest r, int hits, List<String> summary) {}
            @Override public void nextStep() {}
        }, () -> false);
        assertTrue(answer.contains("Stopped after " + CodeChat.MAX_STEPS), answer);
    }
}
