package com.scivicslab.coderaptor;

import com.scivicslab.coderaptor.index.ProjectScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProjectScanner — no Quarkus runtime required.
 */
class ProjectScannerTest {

    private final ProjectScanner scanner = new ProjectScanner();

    @Test
    void isDocProjectReturnsTrueForDocPrefix() {
        Path p = Path.of("/works/doc_SCIVICS001");
        assertTrue(scanner.isDocProject(p));
    }

    @Test
    void isDocProjectReturnsFalseForCodeProject() {
        assertFalse(scanner.isDocProject(Path.of("/works/quarkus-chat-ui")));
        assertFalse(scanner.isDocProject(Path.of("/works/POJO-actor")));
        assertFalse(scanner.isDocProject(Path.of("/works/Turing-workflow")));
    }

    @Test
    void scanExcludesDocProjects(@TempDir Path worksDir) throws IOException {
        // Create mixed directories
        Files.createDirectory(worksDir.resolve("doc_SCIVICS001"));
        Files.createDirectory(worksDir.resolve("doc_SCIVICS002"));
        Files.createDirectory(worksDir.resolve("quarkus-chat-ui"));
        Files.createDirectory(worksDir.resolve("POJO-actor"));

        List<Path> projects = scanner.scanCodeProjects(worksDir);

        assertEquals(2, projects.size());
        assertTrue(projects.stream().anyMatch(p -> p.getFileName().toString().equals("quarkus-chat-ui")));
        assertTrue(projects.stream().anyMatch(p -> p.getFileName().toString().equals("POJO-actor")));
        assertTrue(projects.stream().noneMatch(p -> p.getFileName().toString().startsWith("doc_")));
    }

    @Test
    void scanExcludesHiddenDirectories(@TempDir Path worksDir) throws IOException {
        Files.createDirectory(worksDir.resolve(".code-raptor"));
        Files.createDirectory(worksDir.resolve("my-project"));

        List<Path> projects = scanner.scanCodeProjects(worksDir);

        assertEquals(1, projects.size());
        assertEquals("my-project", projects.getFirst().getFileName().toString());
    }

    @Test
    void scanReturnsEmptyForNonExistentDir() {
        List<Path> projects = scanner.scanCodeProjects(Path.of("/nonexistent/path"));
        assertTrue(projects.isEmpty());
    }
}
