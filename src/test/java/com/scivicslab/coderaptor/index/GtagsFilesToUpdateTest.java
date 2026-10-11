package com.scivicslab.coderaptor.index;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which of one project's files are handed to {@code gtags --single-update}: GPATH lists what the
 * gtags files hold, the disk what is there now, and the GTAGS file's time when they were written.
 */
class GtagsFilesToUpdateTest {

    private static final long GTAGS_TIME = 1_000;

    @Test
    void addedDeletedAndModifiedFilesAreUpdatedAndUntouchedOnesAreNot() {
        Set<String> listed = Set.of("p/Alpha.java", "p/Gone.java", "p/Same.java");
        Map<String, Long> onDisk = Map.of(
                "p/Alpha.java", GTAGS_TIME + 5,   // edited after the gtags files were written
                "p/Same.java",  GTAGS_TIME - 5,   // untouched
                "p/New.java",   GTAGS_TIME - 5);  // not in GPATH yet
        assertEquals(List.of("p/Alpha.java", "p/Gone.java", "p/New.java"),
                GtagsIndexer.filesToUpdate(listed, onDisk, GTAGS_TIME));
    }

    @Test
    void nothingChangedGivesNothing() {
        assertEquals(List.of(), GtagsIndexer.filesToUpdate(
                Set.of("p/A.java"), Map.of("p/A.java", GTAGS_TIME), GTAGS_TIME));
    }
}
