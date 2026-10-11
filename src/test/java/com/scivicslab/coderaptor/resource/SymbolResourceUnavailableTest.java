package com.scivicslab.coderaptor.resource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import com.scivicslab.coderaptor.config.CodeRaptorConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A symbol search whose command cannot be started says so, rather than answering with no hits. */
@DisplayName("GNU Global を起動できないシンボル検索は、空ではなく理由を返す")
class SymbolResourceUnavailableTest {

    @Test
    @DisplayName("コマンドが無ければ SymbolSearchUnavailable になり、理由に GNU Global が出る")
    void missingCommand_isReportedNotEmpty(@TempDir Path works) {
        SymbolResource resource = new SymbolResource();
        resource.config = new CodeRaptorConfig() {
            @Override public Path getWorksDir() { return works; }
        };

        SymbolResource.SymbolSearchUnavailable e = assertThrows(SymbolResource.SymbolSearchUnavailable.class,
                () -> resource.runGlobal(new String[] {"no-such-global-command-for-test", "-x", "anything"}));

        assertTrue(e.getMessage().contains("GNU Global"), e.getMessage());
        assertTrue(e.getMessage().contains("no-such-global-command-for-test"), e.getMessage());
    }
}
