package com.scivicslab.coderaptor.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a project's Javadoc is: maven-javadoc-plugin writes it to target/site/apidocs or, in newer
 * versions and for javadoc:aggregate, to target/reports/apidocs. The index, the project tag and
 * /docs all follow {@link ProjectInfoReader#javadocDir}.
 */
class JavadocDirTest {

    @Test
    void noJavadocGivesEmpty(@TempDir Path project) {
        assertEquals("", ProjectInfoReader.javadocDir(project));
    }

    @Test
    void reportsApidocsIsFound(@TempDir Path project) throws IOException {
        writeIndex(project, "target/reports/apidocs", "2026-10-12T00:00:00Z");
        assertEquals("target/reports/apidocs", ProjectInfoReader.javadocDir(project));
    }

    @Test
    void whenBothExistTheLastWrittenWins(@TempDir Path project) throws IOException {
        writeIndex(project, "target/site/apidocs", "2026-10-01T00:00:00Z");
        writeIndex(project, "target/reports/apidocs", "2026-10-12T00:00:00Z");
        assertEquals("target/reports/apidocs", ProjectInfoReader.javadocDir(project));

        writeIndex(project, "target/site/apidocs", "2026-10-13T00:00:00Z");
        assertEquals("target/site/apidocs", ProjectInfoReader.javadocDir(project));
    }

    @Test
    void readerReportsTheSameDirectoryAsCompiledDocs(@TempDir Path project) throws IOException {
        Files.writeString(project.resolve("pom.xml"), "<project><groupId>g</groupId><artifactId>a</artifactId></project>");
        writeIndex(project, "target/reports/apidocs", "2026-10-12T00:00:00Z");
        assertEquals("target/reports/apidocs", new ProjectInfoReader().read(project).compiledDocs());
    }

    private static void writeIndex(Path project, String dir, String time) throws IOException {
        Path index = project.resolve(dir).resolve("index.html");
        Files.createDirectories(index.getParent());
        Files.writeString(index, "<html><title>x</title></html>");
        Files.setLastModifiedTime(index, FileTime.from(Instant.parse(time)));
    }
}
