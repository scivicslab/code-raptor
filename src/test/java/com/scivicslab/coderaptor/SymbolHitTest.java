package com.scivicslab.coderaptor;

import com.scivicslab.coderaptor.model.SymbolHit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SymbolHit.parse() — no Quarkus runtime required.
 */
class SymbolHitTest {

    @Test
    void parseTypicalGlobalOutput() {
        String line = "addIIActor       110 /home/user/works/Turing-workflow/src/main/java/RunCLI.java system.addIIActor(loaderActor);";
        SymbolHit hit = SymbolHit.parse(line);

        assertNotNull(hit);
        assertEquals("addIIActor", hit.symbol());
        assertEquals(110, hit.line());
        assertEquals("/home/user/works/Turing-workflow/src/main/java/RunCLI.java", hit.file());
        assertEquals("system.addIIActor(loaderActor);", hit.content());
    }

    @Test
    void parseReturnsNullForBlankLine() {
        assertNull(SymbolHit.parse(""));
        assertNull(SymbolHit.parse("   "));
        assertNull(SymbolHit.parse(null));
    }

    @Test
    void parseReturnsNullForInsufficientColumns() {
        // Only 2 columns — not enough
        assertNull(SymbolHit.parse("symbol 42"));
    }

    @Test
    void parseReturnsNullForNonNumericLine() {
        // Line number is not a number
        assertNull(SymbolHit.parse("symbol notANumber /path/to/file.java content"));
    }

    @Test
    void parseHandlesContentWithSpaces() {
        String line = "MyClass      5 /path/MyClass.java   public class MyClass extends Base {";
        SymbolHit hit = SymbolHit.parse(line);

        assertNotNull(hit);
        assertEquals("MyClass", hit.symbol());
        assertEquals(5, hit.line());
        assertEquals("/path/MyClass.java", hit.file());
        assertEquals("public class MyClass extends Base {", hit.content());
    }
}
