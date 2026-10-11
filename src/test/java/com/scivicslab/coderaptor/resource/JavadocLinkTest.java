package com.scivicslab.coderaptor.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A Javadoc search hit, which is a file on disk, turned into the /docs URL that serves it. */
@DisplayName("Javadoc の検索結果を /docs の URL にする")
class JavadocLinkTest {

    private static final Path WORKS = Path.of("/home/devteam/works");
    private static final Map<String, String> COMPILED_DOCS = Map.of(
            "POJO-actor", "target/site/apidocs",
            "no-docs", "");

    private static String link(String file) {
        return JavadocLink.docsUrl(WORKS, Path.of(file), p -> COMPILED_DOCS.getOrDefault(p.getFileName().toString(), ""));
    }

    @Test
    @DisplayName("ドキュメントの置き場の中のファイルは、置き場からの相対パスで /docs/{project}/ に続く")
    void fileUnderTheDocsRootBecomesADocsUrl() {
        assertEquals("/docs/POJO-actor/com/scivicslab/pojoactor/core/ActorRef.html",
                link("/home/devteam/works/POJO-actor/target/site/apidocs/com/scivicslab/pojoactor/core/ActorRef.html"));
    }

    @Test
    @DisplayName("パスの各部分は URL として符号化する")
    void segmentsAreEncoded() {
        assertEquals("/docs/POJO-actor/a%20b/Outer.Inner.html",
                link("/home/devteam/works/POJO-actor/target/site/apidocs/a b/Outer.Inner.html"));
    }

    @Test
    @DisplayName("ドキュメントの置き場が無い、または置き場の外のファイルにはリンクを作らない")
    void noDocsRootOrOutsideItGivesNoLink() {
        assertNull(link("/home/devteam/works/no-docs/target/site/apidocs/X.html"));
        assertNull(link("/home/devteam/works/POJO-actor/target/other/X.html"));
    }

    @Test
    @DisplayName("works の外のファイルにはリンクを作らない")
    void outsideTheWorksDirGivesNoLink() {
        assertNull(link("/tmp/POJO-actor/target/site/apidocs/X.html"));
        assertNull(link("/home/devteam/works"), "the works directory itself names no project");
    }
}
