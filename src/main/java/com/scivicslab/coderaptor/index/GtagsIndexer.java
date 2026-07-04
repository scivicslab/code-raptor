package com.scivicslab.coderaptor.index;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Builds a GNU Global (gtags) symbol index for all code projects under worksDir.
 *
 * <p>The GTAGS, GRTAGS, and GPATH index files are created in worksDir itself,
 * which allows a single {@code global} invocation to cover all projects.
 */
@ApplicationScoped
public class GtagsIndexer {

    private static final Logger LOG = Logger.getLogger(GtagsIndexer.class);

    private static final String[] INDEXED_EXTENSIONS = {".java", ".py", ".go", ".c", ".cpp", ".h"};

    /**
     * Builds (or rebuilds) the gtags index for all source files under worksDir,
     * excluding documentation project directories (those starting with {@code doc_}).
     *
     * @param worksDir root directory where the GTAGS index will be placed
     */
    public void buildIndex(Path worksDir) {
        LOG.info("Building gtags index...");

        // Collect source files via Java (avoids shell pipe/grep complexity)
        List<Path> sourceFiles = collectSourceFiles(worksDir);
        LOG.infof("  Found %d source file(s) to index", sourceFiles.size());

        if (sourceFiles.isEmpty()) {
            LOG.warn("No source files found; skipping gtags.");
            return;
        }

        // Write file list to a temp file
        Path fileList;
        try {
            fileList = Files.createTempFile("code-raptor-gtags-", ".txt");
            Files.write(fileList, sourceFiles.stream().map(Path::toString).toList());
        } catch (IOException e) {
            LOG.errorf(e, "Failed to create gtags file list");
            return;
        }

        // Run: gtags --accept-dotfiles -f <fileList>  (working directory = worksDir)
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "gtags", "--accept-dotfiles", "-f", fileList.toString());
            pb.directory(worksDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                LOG.info("gtags index built successfully.");
            } else {
                LOG.warnf("gtags exited with code %d. Output: %s", exitCode, output);
            }
        } catch (IOException | InterruptedException e) {
            LOG.errorf(e, "Failed to run gtags");
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            try {
                Files.deleteIfExists(fileList);
            } catch (IOException ignored) {}
        }
    }

    // Directory names to skip at any depth (build artifacts, VCS, deps)
    private static final java.util.Set<String> EXCLUDED_DIRS = java.util.Set.of(
            "target", ".git", ".idea", "node_modules", ".gradle", "build",
            "__pycache__", ".venv", "vendor");

    /**
     * Walks worksDir with no depth limit and collects source files.
     * Skips: doc_* top-level directories, hidden top-level directories,
     * and build/VCS directories (target/, .git/, etc.) at any depth.
     */
    private List<Path> collectSourceFiles(Path worksDir) {
        List<Path> files = new ArrayList<>();

        // Use a custom walk that prunes excluded directories
        try {
            java.nio.file.FileVisitor<Path> visitor = new java.nio.file.SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                    String name = dir.getFileName().toString();
                    // Skip excluded dirs at any level
                    if (EXCLUDED_DIRS.contains(name)) {
                        return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                    }
                    // Skip top-level doc_* and hidden dirs
                    if (dir.getParent() != null && dir.getParent().equals(worksDir)) {
                        if (name.startsWith("doc_") || name.startsWith(".")) {
                            return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                    if (hasIndexedExtension(file.getFileName().toString())) {
                        files.add(file);
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            };
            Files.walkFileTree(worksDir, visitor);
        } catch (IOException e) {
            LOG.errorf(e, "Error walking worksDir: %s", worksDir);
        }
        return files;
    }

    private boolean hasIndexedExtension(String filename) {
        for (String ext : INDEXED_EXTENSIONS) {
            if (filename.endsWith(ext)) return true;
        }
        return false;
    }
}
