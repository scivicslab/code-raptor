package com.scivicslab.coderaptor.service;

import com.scivicslab.coderaptor.index.ProjectInfoReader;
import com.scivicslab.coderaptor.model.ProjectInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

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
 * running one's ID.
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
                "rebuild " + target.name().toLowerCase(Locale.ROOT), out -> run(target, out));
        lastRebuild = id;
        return id;
    }

    private int run(Target target, Consumer<String> out) {
        List<String> failed = new ArrayList<>();
        if (target == Target.SYMBOL || target == Target.ALL) {
            out.accept("Symbol: rebuilding the gtags files…");
            out.accept("Symbol: " + indexService.rebuildSymbolIndex());
        }
        if (target == Target.JAVADOC || target == Target.ALL) {
            failed.addAll(generateAllDocs(out));
            out.accept("Javadoc: rebuilding the Javadoc index…");
            out.accept("Javadoc: " + indexService.rebuildJavadocIndex());
        }
        if (target == Target.PROJECTS || target == Target.ALL) {
            out.accept("Projects: rebuilding the project list index…");
            out.accept("Projects: " + indexService.rebuildProjectIndex());
        }
        if (failed.isEmpty()) {
            out.accept("Done.");
            return 0;
        }
        out.accept("Done. Docs failed in " + failed.size() + " project(s): " + String.join(", ", failed));
        return 1;
    }

    /** Runs the Docs command of every project that has one, one after another; returns the failed ones. */
    private List<String> generateAllDocs(Consumer<String> out) {
        List<ProjectInfo> withDocs = indexService.codeProjects().stream()
                .map(infoReader::read)
                .filter(p -> buildService.hasDocs(p.buildSystem()))
                .toList();
        out.accept("Javadoc: running Docs in " + withDocs.size() + " project(s)…");
        List<String> failed = new ArrayList<>();
        Path works = indexService.worksDir();
        for (int i = 0; i < withDocs.size(); i++) {
            ProjectInfo p = withDocs.get(i);
            String step = "(" + (i + 1) + "/" + withDocs.size() + ") " + p.name() + " docs";
            Deque<String> tail = new ArrayDeque<>();
            long started = System.nanoTime();
            int code = buildService.runDocs(works.resolve(p.name()), p.buildSystem(), line -> {
                if (tail.size() == FAILURE_TAIL) tail.removeFirst();
                tail.addLast(line.length() > MAX_LINE ? line.substring(0, MAX_LINE) + "…" : line);
            });
            long seconds = (System.nanoTime() - started) / 1_000_000_000L;
            if (code == 0) {
                out.accept(step + " ✓ " + seconds + "s");
            } else {
                out.accept(step + " ✗ exit " + code + " " + seconds + "s");
                tail.forEach(line -> out.accept("    " + line));
                failed.add(p.name());
            }
        }
        return failed;
    }
}
