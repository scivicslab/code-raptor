package com.scivicslab.coderaptor.service;

import com.scivicslab.coderaptor.config.CodeRaptorConfig;
import com.scivicslab.coderaptor.index.GtagsIndexer;
import com.scivicslab.coderaptor.index.JavadocIndexer;
import com.scivicslab.coderaptor.index.ProjectIndexer;
import com.scivicslab.coderaptor.index.ProjectInfoReader;
import com.scivicslab.coderaptor.index.ProjectScanner;
import com.scivicslab.coderaptor.model.ProjectInfo;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.util.List;

/**
 * Orchestrates the full reindex pipeline:
 * <ol>
 *   <li>Scan works-dir for code projects</li>
 *   <li>Build gtags symbol index</li>
 *   <li>Build Lucene project-metadata index</li>
 *   <li>Build Lucene Javadoc index</li>
 * </ol>
 *
 * <p>Runs automatically on startup and on demand via {@link #reindex()}.
 */
@ApplicationScoped
public class IndexService {

    private static final Logger LOG = Logger.getLogger(IndexService.class);

    @Inject CodeRaptorConfig config;
    @Inject ProjectScanner   scanner;
    @Inject ProjectInfoReader infoReader;
    @Inject GtagsIndexer     gtagsIndexer;
    @Inject ProjectIndexer   projectIndexer;
    @Inject JavadocIndexer   javadocIndexer;

    /** Triggered by Quarkus at application startup. */
    void onStart(@Observes StartupEvent ev) {
        LOG.infof("code-raptor starting. works-dir = %s", config.getWorksDir());
        reindex();
    }

    /**
     * Runs the full reindex pipeline synchronously.
     * Safe to call concurrently (each call rebuilds from scratch).
     */
    public void reindex() {
        Path worksDir = config.getWorksDir();
        LOG.info("=== Reindex started ===");

        List<Path> projectDirs = scanner.scanCodeProjects(worksDir);

        // Build gtags symbol index (needs raw paths)
        gtagsIndexer.buildIndex(worksDir);

        // Convert paths → ProjectInfo (shared by both Lucene indexes)
        List<ProjectInfo> projects = projectDirs.stream()
                .map(infoReader::read)
                .toList();

        // Build Lucene project-metadata index (powers GET /api/projects?q=...)
        projectIndexer.buildIndex(projects);

        // Build Lucene Javadoc index (powers GET /api/javadoc?q=...)
        javadocIndexer.buildIndex(projectDirs);

        LOG.info("=== Reindex complete ===");
    }
}
