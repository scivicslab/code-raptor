package com.scivicslab.coderaptor.index;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scans the works directory and returns the list of code projects.
 *
 * <p>Detection rule: direct subdirectories of worksDir whose name does NOT
 * start with {@code doc_} are treated as code projects. Directories starting
 * with {@code doc_} are documentation projects and are excluded.
 */
@ApplicationScoped
public class ProjectScanner {

    private static final Logger LOG = Logger.getLogger(ProjectScanner.class);

    /**
     * Scans {@code worksDir} and returns code project paths.
     *
     * @param worksDir root directory (e.g. ~/works)
     * @return list of direct subdirectories that are code projects
     */
    public List<Path> scanCodeProjects(Path worksDir) {
        if (!Files.isDirectory(worksDir)) {
            LOG.warnf("works-dir does not exist or is not a directory: %s", worksDir);
            return Collections.emptyList();
        }

        try (Stream<Path> stream = Files.list(worksDir)) {
            List<Path> projects = stream
                    .filter(Files::isDirectory)
                    .filter(p -> !isDocProject(p))
                    .filter(p -> !isHidden(p))
                    .sorted()
                    .toList();

            LOG.infof("Found %d code project(s) in %s", projects.size(), worksDir);
            projects.forEach(p -> LOG.debugf("  project: %s", p.getFileName()));
            return projects;

        } catch (IOException e) {
            LOG.errorf(e, "Failed to scan works-dir: %s", worksDir);
            return Collections.emptyList();
        }
    }

    /** Returns true if the directory is a documentation project (name starts with doc_). */
    public boolean isDocProject(Path dir) {
        return dir.getFileName().toString().startsWith("doc_");
    }

    /** Returns true if the directory name starts with a dot (hidden). */
    private boolean isHidden(Path dir) {
        return dir.getFileName().toString().startsWith(".");
    }
}
