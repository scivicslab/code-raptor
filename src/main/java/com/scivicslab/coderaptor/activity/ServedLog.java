package com.scivicslab.coderaptor.activity;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * What this instance has been asked for, most recent first
 * ({@code ActivitySummary_260905_oo01}).
 *
 * <p>Kept on the server rather than in the browser: the question is what this instance is doing,
 * and the answer has to survive the tab that caused it being closed. Several people connected to
 * one instance land in one list, which is right — the question is about the instance, not about
 * who asked.</p>
 */
@ApplicationScoped
public class ServedLog {

    /** How many entries are kept. Enough for the Instance Detail screen to show a session. */
    private static final int LIMIT = 20;

    /**
     * One thing this instance was asked for.
     *
     * @param key   what identifies it — a document path, a search query — used to keep the list
     *              free of the same thing repeated
     * @param label what it is, in words a reader recognises
     * @param at    when it was asked for, in epoch milliseconds
     */
    public record Served(String key, String label, long at) {}

    private final Deque<Served> served = new ConcurrentLinkedDeque<>();

    /**
     * Records one request.
     *
     * <p>Asking for the same thing again moves it to the front rather than adding a second entry:
     * reloading one Javadoc page five times is one thing being looked at, not five.</p>
     *
     * @param key   what identifies it
     * @param label what it is, in words
     */
    public void note(String key, String label) {
        if (label == null || label.isBlank()) return;
        served.removeIf(v -> v.key().equals(key));
        served.addFirst(new Served(key, label, System.currentTimeMillis()));
        while (served.size() > LIMIT) served.pollLast();
    }

    /**
     * @return what was asked for, most recent first
     */
    public List<Served> recent() {
        return new ArrayList<>(served);
    }
}
