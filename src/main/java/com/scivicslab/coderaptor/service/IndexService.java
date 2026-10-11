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
        LOG.info("=== Reindex started ===");
        rebuildSymbolIndex();
        rebuildProjectIndex();
        rebuildJavadocIndex();
        LOG.info("=== Reindex complete ===");
    }

    /** The directory the projects are in. */
    public Path worksDir() {
        return config.getWorksDir();
    }

    /** The code project directories under works-dir. */
    public List<Path> codeProjects() {
        return scanner.scanCodeProjects(config.getWorksDir());
    }

    /**
     * Rebuilds the gtags files in works-dir (powers GET /api/symbol).
     *
     * @return one line saying what was done
     */
    public String rebuildSymbolIndex() {
        return gtagsIndexer.buildIndex(config.getWorksDir());
    }

    /**
     * Rebuilds the Lucene project-metadata index (powers GET /api/projects?q=...).
     *
     * @return one line saying what was done
     */
    public String rebuildProjectIndex() {
        List<ProjectInfo> projects = codeProjects().stream().map(infoReader::read).toList();
        projectIndexer.buildIndex(projects);
        return "project list index: " + projects.size() + " project(s)";
    }

    /** Brings the gtags files up to date for one project's source files only. */
    public String updateSymbolIndex(String project) {
        return gtagsIndexer.updateProject(config.getWorksDir(), project);
    }

    /** Replaces one project's entry in the Lucene project-metadata index. */
    public String updateProjectIndex(String project) {
        boolean ok = projectIndexer.updateProject(infoReader.read(config.getWorksDir().resolve(project)));
        return ok ? "project list index: replaced the entry of " + project
                  : "project list index: could not write the entry of " + project;
    }

    /** Replaces one project's pages in the Lucene Javadoc index with its Javadoc HTML on disk. */
    public String updateJavadocIndex(String project) {
        int pages = javadocIndexer.updateProject(config.getWorksDir().resolve(project));
        return pages < 0 ? "Javadoc index: could not write the pages of " + project
                         : "Javadoc index: " + pages + " page(s) of " + project;
    }

    /**
     * Rebuilds the Lucene Javadoc index from the Javadoc HTML already on disk
     * (powers GET /api/javadoc?q=...).
     *
     * @return one line saying what was done
     */
    public String rebuildJavadocIndex() {
        List<Path> projectDirs = codeProjects();
        javadocIndexer.buildIndex(projectDirs);
        long withJavadoc = projectDirs.stream()
                .filter(dir -> !ProjectInfoReader.javadocDir(dir).isEmpty()).count();
        return "Javadoc index: " + withJavadoc + " project(s) with Javadoc";
    }
}
