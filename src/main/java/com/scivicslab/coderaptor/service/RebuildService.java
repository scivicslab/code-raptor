package com.scivicslab.coderaptor.service;

import com.scivicslab.coderaptor.index.ProjectInfoReader;
import com.scivicslab.coderaptor.model.ProjectInfo;
import jakarta.enterprise.context.ApplicationScoped;
import com.scivicslab.coderaptor.service.BuildService.JobContext;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Rebuilds, for every project at once, what one of the portal's searches reads.
 *
 * <ul>
 *   <li>{@code symbol}   — the gtags files (Symbol search)</li>
 *   <li>{@code javadoc}  — each project's Javadoc HTML through its Docs command, then the Lucene
 *                          Javadoc index (Javadoc search)</li>
 *   <li>{@code projects} — the Lucene project-list index (the project list)</li>
 *   <li>{@code all}      — the three in that order</li>
 * </ul>
 *
 * <p>The rebuild runs as a build job, so the portal shows its lines in the same log view as a
 * Build or Docs. Only one rebuild runs at a time; asking for another while one runs returns the
 * running one's ID. A stop ({@link BuildService#stop}) ends the running command and skips the
 * remaining steps, the index rebuilds included.
 */
@ApplicationScoped
public class RebuildService {

    /** Last lines of a failed Docs command kept in the rebuild log. */
    private static final int FAILURE_TAIL = 8;
    private static final int MAX_LINE = 240;

    /** What a rebuild can be asked to rebuild. */
    public enum Target {
        SYMBOL, JAVADOC, PROJECTS, ALL;

        /** Parses the lower-case name the portal sends; throws for anything else. */
        public static Target parse(String name) {
            if (name != null) {
                for (Target t : values()) if (t.name().toLowerCase(Locale.ROOT).equals(name)) return t;
            }
            throw new IllegalArgumentException("Unknown rebuild target: " + name
                    + " (expected symbol, javadoc, projects or all)");
        }
    }

    @Inject BuildService buildService;
    @Inject IndexService indexService;
    @Inject ProjectInfoReader infoReader;

    /** ID of the last rebuild started; guarded by this service's lock. */
    private String lastRebuild;

    /** Starts a rebuild of {@code target}, or returns the running rebuild's ID. */
    public synchronized String start(Target target) {
        if (lastRebuild != null && buildService.get(lastRebuild)
                .map(s -> Boolean.TRUE.equals(s.get("running"))).orElse(false)) {
            return lastRebuild;
        }
        String id = buildService.startJob("all projects",
                "rebuild " + target.name().toLowerCase(Locale.ROOT), job -> run(target, job));
        lastRebuild = id;
        return id;
    }

    /** Exit code reported for a rebuild that was stopped. */
    private static final int STOPPED = 130;

    private int run(Target target, JobContext job) {
        List<String> failed = new ArrayList<>();
        if (target == Target.SYMBOL || target == Target.ALL) {
            if (job.stopRequested()) return stopped(job);
            job.line("Symbol: rebuilding the gtags files…");
            job.line("Symbol: " + indexService.rebuildSymbolIndex());
        }
        if (target == Target.JAVADOC || target == Target.ALL) {
            failed.addAll(generateAllDocs(job));
            if (job.stopRequested()) return stopped(job);
            job.line("Javadoc: rebuilding the Javadoc index…");
            job.line("Javadoc: " + indexService.rebuildJavadocIndex());
        }
        if (target == Target.PROJECTS || target == Target.ALL) {
            if (job.stopRequested()) return stopped(job);
            job.line("Projects: rebuilding the project list index…");
            job.line("Projects: " + indexService.rebuildProjectIndex());
        }
        if (failed.isEmpty()) {
            job.line("Done.");
            return 0;
        }
        job.line("Done. Docs failed in " + failed.size() + " project(s): " + String.join(", ", failed));
        return 1;
    }

    /** Ends a stopped rebuild; the steps after the one that was running are not done. */
    private static int stopped(JobContext job) {
        job.line("Stopped. The remaining steps were not run.");
        return STOPPED;
    }

    /**
     * Runs the Docs command of every project that has one, one after another, until the job is
     * asked to stop; returns the projects whose command failed.
     */
    private List<String> generateAllDocs(JobContext job) {
        List<ProjectInfo> withDocs = indexService.codeProjects().stream()
                .map(infoReader::read)
                .filter(p -> buildService.hasDocs(p.buildSystem()))
                .toList();
        job.line("Javadoc: running Docs in " + withDocs.size() + " project(s)…");
        List<String> failed = new ArrayList<>();
        Path works = indexService.worksDir();
        for (int i = 0; i < withDocs.size() && !job.stopRequested(); i++) {
            ProjectInfo p = withDocs.get(i);
            String step = "(" + (i + 1) + "/" + withDocs.size() + ") " + p.name() + " docs";
            job.line(step + "…");
            Deque<String> tail = new ArrayDeque<>();
            long started = System.nanoTime();
            int code = buildService.runDocs(works.resolve(p.name()), p.buildSystem(), job, line -> {
                if (tail.size() == FAILURE_TAIL) tail.removeFirst();
                tail.addLast(line.length() > MAX_LINE ? line.substring(0, MAX_LINE) + "…" : line);
            });
            long seconds = (System.nanoTime() - started) / 1_000_000_000L;
            if (job.stopRequested()) {
                job.line(step + " stopped after " + seconds + "s");
            } else if (code == 0) {
                job.line(step + " ✓ " + seconds + "s");
            } else {
                job.line(step + " ✗ exit " + code + " " + seconds + "s");
                tail.forEach(line -> job.line("    " + line));
                failed.add(p.name());
            }
        }
        return failed;
    }
}
