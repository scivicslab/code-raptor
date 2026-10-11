package com.scivicslab.coderaptor.index;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
     * @return one line saying what was done, for a person watching the rebuild
     */
    public String buildIndex(Path worksDir) {
        LOG.info("Building gtags index...");

        // Collect source files via Java (avoids shell pipe/grep complexity)
        List<Path> sourceFiles = collectSourceFiles(worksDir);
        LOG.infof("  Found %d source file(s) to index", sourceFiles.size());

        if (sourceFiles.isEmpty()) {
            LOG.warn("No source files found; skipping gtags.");
            return "gtags skipped: no source files found";
        }

        // Write file list to a temp file
        Path fileList;
        try {
            fileList = Files.createTempFile("code-raptor-gtags-", ".txt");
            Files.write(fileList, sourceFiles.stream().map(Path::toString).toList());
        } catch (IOException e) {
            LOG.errorf(e, "Failed to create gtags file list");
            return "gtags failed: cannot write the file list: " + e.getMessage();
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
                return "gtags indexed " + sourceFiles.size() + " source file(s)";
            }
            LOG.warnf("gtags exited with code %d. Output: %s", exitCode, output);
            return "gtags failed with exit code " + exitCode + ": " + output.strip();
        } catch (IOException | InterruptedException e) {
            LOG.errorf(e, "Failed to run gtags");
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "gtags failed: " + e.getMessage();
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
        return collectSourceFiles(worksDir, worksDir);
    }

    /** The source files gtags indexes under {@code start}, which is works-dir or one project in it. */
    private List<Path> collectSourceFiles(Path worksDir, Path start) {
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
            Files.walkFileTree(start, visitor);
        } catch (IOException e) {
            LOG.errorf(e, "Error walking worksDir: %s", worksDir);
        }
        return files;
    }

    /** More changed files than this are faster to index by rebuilding everything. */
    static final int MAX_SINGLE_UPDATES = 150;

    /**
     * Brings the gtags files up to date for one project only: each of its source files that was
     * added, deleted or modified since the GTAGS file was written is handed to
     * {@code gtags --single-update}, which leaves every other file's tags as they are.
     *
     * <p>{@code gtags -i -f <list>} is not used: it treats files missing from the list as deleted,
     * which drops every other project.
     *
     * @return one line saying what was done
     */
    public String updateProject(Path worksDir, String project) {
        Path gtags = worksDir.resolve("GTAGS");
        if (!Files.exists(gtags)) {
            return buildIndex(worksDir) + " (no GTAGS yet, so every project was indexed)";
        }
        long gtagsTime;
        Set<String> listed;
        try {
            gtagsTime = Files.getLastModifiedTime(gtags).toMillis();
            listed = listedFiles(worksDir, project);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return "gtags failed: cannot read GPATH: " + e.getMessage();
        }
        Map<String, Long> current = new HashMap<>();
        for (Path file : collectSourceFiles(worksDir, worksDir.resolve(project))) {
            try {
                current.put(worksDir.relativize(file).toString(), Files.getLastModifiedTime(file).toMillis());
            } catch (IOException ignored) {}
        }
        List<String> changed = filesToUpdate(listed, current, gtagsTime);
        if (changed.isEmpty()) {
            return "gtags: no source file of " + project + " changed";
        }
        if (changed.size() > MAX_SINGLE_UPDATES) {
            return buildIndex(worksDir) + " (" + changed.size() + " files of " + project
                    + " changed, so every project was indexed)";
        }
        for (String file : changed) {
            try {
                Process p = new ProcessBuilder("gtags", "--accept-dotfiles", "--single-update", file)
                        .directory(worksDir.toFile()).redirectErrorStream(true).start();
                String output = new String(p.getInputStream().readAllBytes());
                int code = p.waitFor();
                if (code != 0) return "gtags failed on " + file + " with exit code " + code + ": " + output.strip();
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                return "gtags failed on " + file + ": " + e.getMessage();
            }
        }
        return "gtags updated " + changed.size() + " file(s) of " + project;
    }

    /**
     * The files of one project to hand to {@code gtags --single-update}: added (not yet in GPATH),
     * deleted (in GPATH, gone from disk), and modified after the GTAGS file was written.
     *
     * @param listed   the project's files GPATH holds, relative to works-dir
     * @param current  the project's source files on disk → modification time in milliseconds
     */
    static List<String> filesToUpdate(Set<String> listed, Map<String, Long> current, long gtagsTime) {
        Set<String> changed = new TreeSet<>();
        for (Map.Entry<String, Long> e : current.entrySet()) {
            if (!listed.contains(e.getKey()) || e.getValue() > gtagsTime) changed.add(e.getKey());
        }
        for (String file : listed) {
            if (!current.containsKey(file)) changed.add(file);
        }
        return new ArrayList<>(changed);
    }

    /** The project's files in GPATH, read with {@code global -P}, relative to works-dir. */
    private static Set<String> listedFiles(Path worksDir, String project) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("global", "-P").directory(worksDir.toFile()).start();
        String prefix = project + "/";
        Set<String> files = new HashSet<>();
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String path = line.startsWith("./") ? line.substring(2) : line;
                if (path.startsWith(prefix)) files.add(path);
            }
        }
        int code = p.waitFor();
        if (code != 0) throw new IOException("global -P exited with code " + code);
        return files;
    }

    private boolean hasIndexedExtension(String filename) {
        for (String ext : INDEXED_EXTENSIONS) {
            if (filename.endsWith(ext)) return true;
        }
        return false;
    }
}
