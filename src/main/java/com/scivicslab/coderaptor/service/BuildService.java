package com.scivicslab.coderaptor.service;

import com.scivicslab.coderaptor.config.CodeRaptorConfig;
import com.scivicslab.pojoactor.core.ActorRef;
import com.scivicslab.pojoactor.core.ActorSystem;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages asynchronous project build jobs using POJO-actor.
 *
 * <p>Each build runs in a managed thread pool. Build output and state are
 * confined to a per-job {@link ActorRef}, so no {@code volatile},
 * {@code synchronized}, or {@code StringBuffer} is needed.
 *
 * <p>Usage:
 * <pre>
 *   String id = buildService.start("my-project", "build", "Maven");
 *   // poll:
 *   buildService.get(id).ifPresent(status -> ...);
 * </pre>
 */
@ApplicationScoped
public class BuildService {

    private static final Logger LOG = Logger.getLogger(BuildService.class);
    private static final int MAX_OUTPUT = 64 * 1024; // 64 KB cap per job

    /**
     * Doxygen documentation command for C/C++ projects.
     *
     * <p>When the project has no Doxyfile, generate a default one and append
     * overrides (Doxygen honours the last assignment of each tag) so that the
     * generated docs are useful: recurse into subdirectories and document all
     * entities even without doc comments. The default HTML output lands in
     * {@code ./html}, which {@code ProjectInfoReader.detectCompiledDocs} detects.
     * When a Doxyfile already exists (e.g. cichlid), it is used as-is.
     */
    private static final String DOXYGEN_DOCS_CMD =
            "if [ ! -f Doxyfile ]; then "
            + "doxygen -g Doxyfile >/dev/null && "
            + "{ echo 'RECURSIVE = YES'; echo 'EXTRACT_ALL = YES'; echo 'QUIET = YES'; } >> Doxyfile; "
            + "fi; doxygen";

    @Inject
    CodeRaptorConfig config;

    private ActorSystem system;
    private ExecutorService pool;
    private final AtomicInteger seq = new AtomicInteger();

    @PostConstruct
    void init() {
        system = new ActorSystem("build-system");
        system.addManagedThreadPool(8);       // index 1: process-runner pool
        pool   = system.getManagedThreadPool(1);
    }

    @PreDestroy
    void destroy() {
        system.terminate();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Starts an asynchronous build and returns the job ID immediately.
     *
     * @param project     project directory name (no path separators allowed)
     * @param task        "build" or "docs"
     * @param buildSystem "Maven", "Gradle", "Cargo", "Go Modules", etc.
     * @throws IllegalArgumentException if arguments are invalid or unsupported
     */
    public String start(String project, String task, String buildSystem) {
        validateProject(project);
        Path projectDir = config.getWorksDir().resolve(project);
        if (!Files.isDirectory(projectDir)) {
            throw new IllegalArgumentException("Project directory not found: " + project);
        }
        String[] cmd = command(buildSystem, task);
        if (cmd == null) {
            throw new IllegalArgumentException(
                    "No " + task + " command defined for build system: " + buildSystem);
        }

        String id = "b" + seq.incrementAndGet();
        ActorRef<BuildJob> ref = system.actorOf(id, new BuildJob(id, project, task));

        // Run the external process in the managed pool.
        // State is updated through the actor queue (tell), so no synchronisation is needed
        // in BuildJob itself.
        pool.submit(() -> runProcess(ref, cmd, projectDir, id));

        LOG.infof("Build started: id=%s  project=%s  task=%s  system=%s",
                id, project, task, buildSystem);
        return id;
    }

    /**
     * Returns a status snapshot for the given job ID, or empty if not found.
     * The snapshot is a plain {@code Map} safe to serialise as JSON.
     */
    public Optional<Map<String, Object>> get(String id) {
        if (!system.isAlive(id)) return Optional.empty();
        ActorRef<BuildJob> ref = system.getActor(id);
        return Optional.of(ref.ask(BuildJob::toStatus).join());
    }

    // ── Process runner ────────────────────────────────────────────────────────

    private void runProcess(ActorRef<BuildJob> ref, String[] cmd, Path dir, String id) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader br =
                         new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    final String l = line;
                    // Each tell() is queued; ask() from the REST thread is also queued →
                    // no race condition, no volatile needed in BuildJob.
                    ref.tell(j -> j.appendLine(l));
                }
            }
            int code = process.waitFor();
            ref.tell(j -> j.finish(code));
            LOG.infof("Build finished: id=%s  exitCode=%d", id, code);

        } catch (IOException | InterruptedException e) {
            ref.tell(j -> j.appendLine("[Build error: " + e.getMessage() + "]"));
            ref.tell(j -> j.finish(-1));
            LOG.errorf(e, "Build job %s failed with exception", id);
        }
    }

    // ── Command table ─────────────────────────────────────────────────────────

    /** Maps (buildSystem, task) → shell command array. Returns null if unsupported. */
    private static String[] command(String buildSystem, String task) {
        if (buildSystem == null || task == null) return null;
        return switch (buildSystem) {
            case "Maven" -> switch (task) {
                case "build" -> new String[]{"bash", "-c", "rm -rf target && mvn install"};
                case "docs"  -> new String[]{"bash", "-c",
                        "JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US'" +
                        " mvn javadoc:javadoc -Dlocale=en_US"};
                default -> null;
            };
            case "Gradle" -> switch (task) {
                case "build" -> new String[]{"bash", "-c", "./gradlew build"};
                case "docs"  -> new String[]{"bash", "-c",
                        "JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US'" +
                        " ./gradlew javadoc"};
                default -> null;
            };
            case "Cargo" -> switch (task) {
                case "build" -> new String[]{"bash", "-c", "cargo build"};
                case "docs"  -> new String[]{"bash", "-c", "RUSTDOCFLAGS='' cargo doc"};
                default -> null;
            };
            case "Go Modules" -> switch (task) {
                case "build" -> new String[]{"bash", "-c", "go build ./..."};
                default -> null;
            };
            // ── C/C++ build systems ───────────────────────────────────────────
            // Docs are generated with Doxygen (uses the project's ./Doxyfile).
            // The Docs button is only shown when a Doxyfile exists (see PortalResource).
            case "Autotools" -> switch (task) {
                // ./configure is generated by autoreconf/bootstrap. If it is missing,
                // run ./bootstrap first, then configure && make.
                case "build" -> new String[]{"bash", "-c",
                        "{ [ -x ./configure ] || ./bootstrap; } && ./configure && make"};
                case "docs"  -> new String[]{"bash", "-c", DOXYGEN_DOCS_CMD};
                default -> null;
            };
            case "CMake" -> switch (task) {
                case "build" -> new String[]{"bash", "-c",
                        "cmake -S . -B build && cmake --build build"};
                case "docs"  -> new String[]{"bash", "-c", DOXYGEN_DOCS_CMD};
                default -> null;
            };
            case "Make" -> switch (task) {
                case "build" -> new String[]{"bash", "-c", "make"};
                case "docs"  -> new String[]{"bash", "-c", DOXYGEN_DOCS_CMD};
                default -> null;
            };
            default -> null;
        };
    }

    private static void validateProject(String project) {
        if (project == null || project.isBlank()
                || project.contains("/") || project.contains("\\")
                || project.contains("..")) {
            throw new IllegalArgumentException("Invalid project name: " + project);
        }
    }

    // ── BuildJob (actor state) ────────────────────────────────────────────────

    /**
     * Mutable state for a single build job.
     *
     * <p>All fields are accessed exclusively through the actor's message queue,
     * so no {@code volatile}, {@code synchronized}, or {@code AtomicXxx} is needed.
     */
    static class BuildJob {

        private final String        id;
        private final String        project;
        private final String        task;
        private       boolean       running   = true;
        private       int           exitCode  = -1;
        private final StringBuilder output    = new StringBuilder();
        private       boolean       truncated = false;

        BuildJob(String id, String project, String task) {
            this.id = id; this.project = project; this.task = task;
        }

        void appendLine(String line) {
            if (output.length() < MAX_OUTPUT) {
                output.append(line).append('\n');
            } else if (!truncated) {
                output.append("\n[Output truncated at 64 KB]\n");
                truncated = true;
            }
        }

        void finish(int code) {
            running  = false;
            exitCode = code;
        }

        /** Returns a JSON-serialisable snapshot of the current state. */
        Map<String, Object> toStatus() {
            return Map.of(
                    "id",       id,
                    "project",  project,
                    "task",     task,
                    "running",  running,
                    "exitCode", exitCode,
                    "output",   output.toString()
            );
        }
    }
}
