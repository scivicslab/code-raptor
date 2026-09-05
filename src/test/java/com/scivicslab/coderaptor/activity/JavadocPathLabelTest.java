package com.scivicslab.coderaptor.activity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pure unit test for reading a compiled-documentation path.
 *
 * <p>Exercises the load-bearing path: what this returns is drawn on AI-workspace's Instances
 * screen, so a path that names nothing a reader recognises has to come back as nothing rather than
 * as a file name ({@code ActivitySummary_260905_oo01}).</p>
 */
class JavadocPathLabelTest {

    @Test
    void aClassPage_isTheClassAsItIsNormallyWritten() {
        assertEquals("com.scivicslab.pojoactor.core.ActorRef",
                JavadocPathLabel.of("com/scivicslab/pojoactor/core/ActorRef.html"));
    }

    @Test
    void anInnerClassPage_keepsTheDollarNameItIsGeneratedUnder() {
        assertEquals("com.scivicslab.chatui.core.actor.ChatActor.HistoryEntry",
                JavadocPathLabel.of("com/scivicslab/chatui/core/actor/ChatActor.HistoryEntry.html"));
    }

    @Test
    void aPackagePage_saysItIsAPackage() {
        assertEquals("com.scivicslab.pojoactor.core パッケージ",
                JavadocPathLabel.of("com/scivicslab/pojoactor/core/package-summary.html"));
    }

    @Test
    void theGeneratorsOwnPages_nameNothingBeingRead() {
        assertNull(JavadocPathLabel.of("index.html"));
        assertNull(JavadocPathLabel.of("allclasses-index.html"));
        assertNull(JavadocPathLabel.of("index-all.html"));
        assertNull(JavadocPathLabel.of("index-3.html"));
        assertNull(JavadocPathLabel.of("help-doc.html"));
        assertNull(JavadocPathLabel.of("deprecated-list.html"));
    }

    @Test
    void everythingThatIsNotAPage_isNothing() {
        // One Javadoc page pulls in these by the dozen; none of them says what is being read.
        assertNull(JavadocPathLabel.of("stylesheet.css"));
        assertNull(JavadocPathLabel.of("script-dir/jquery-3.7.1.min.js"));
        assertNull(JavadocPathLabel.of("resource-files/glass.png"));
        assertNull(JavadocPathLabel.of(""));
        assertNull(JavadocPathLabel.of(null));
    }
}
