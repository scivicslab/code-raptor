package com.scivicslab.coderaptor.activity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test for the list of what was asked for.
 *
 * <p>Exercises the load-bearing path: the front of this list is the whole answer the Instances
 * screen shows, so the newest entry must be first and one page reloaded must not fill the list
 * ({@code ActivitySummary_260905_oo01}).</p>
 */
class ServedLogTest {

    @Test
    void theNewestIsFirst() {
        ServedLog log = new ServedLog();

        log.note("a", "ActorRef");
        log.note("b", "ChatActor");

        assertEquals(List.of("ChatActor", "ActorRef"),
                log.recent().stream().map(ServedLog.Served::label).toList());
    }

    @Test
    void thesamePageAgain_movesToTheFrontInsteadOfBeingAddedTwice() {
        ServedLog log = new ServedLog();

        log.note("a", "ActorRef");
        log.note("b", "ChatActor");
        log.note("a", "ActorRef");

        assertEquals(List.of("ActorRef", "ChatActor"),
                log.recent().stream().map(ServedLog.Served::label).toList());
    }

    @Test
    void aLabelOfNothing_isNotRecorded() {
        ServedLog log = new ServedLog();

        log.note("a", null);
        log.note("b", "  ");

        assertTrue(log.recent().isEmpty());
    }

    @Test
    void theListStaysShort() {
        ServedLog log = new ServedLog();

        for (int i = 0; i < 100; i++) log.note("k" + i, "C" + i);

        assertEquals(20, log.recent().size());
        assertEquals("C99", log.recent().get(0).label());
    }
}
