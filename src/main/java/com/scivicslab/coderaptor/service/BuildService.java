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
import java.util.function.Consumer;
import java.util.function.Function;

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

    /**
     * Asks a running job to stop: the command it is running ends together with the processes
     * the command started, and a rebuild does not go on to its next step.
     *
     * @return false when there is no such job
     */
    public boolean stop(String id) {
        if (!system.isAlive(id)) return false;
        ActorRef<BuildJob> ref = system.getActor(id);
        ref.tell(BuildJob::stop);
        LOG.infof("Stop requested: id=%s", id);
        return true;
    }

    /** What a job's work sees of its job: where its lines go, and whether it was asked to stop. */
    public static final class JobContext {
        private final ActorRef<BuildJob> ref;

        private JobContext(ActorRef<BuildJob> ref) { this.ref = ref; }

        /** Appends a line to the job's output. */
        public void line(String line) { ref.tell(j -> j.appendLine(line)); }

        /** True once someone asked the job to stop. */
        public boolean stopRequested() { return ref.ask(BuildJob::stopRequested).join(); }

        private void started(Process process) { ref.tell(j -> j.attach(process)); }
    }

    /**
     * Starts a job whose output and outcome are shown like a build's, and returns its ID
     * immediately. {@code work} runs in the build pool, writes lines through the context,
     * and returns the exit code to report (0 = success).
     */
    public String startJob(String project, String task, Function<JobContext, Integer> work) {
        String id = "b" + seq.incrementAndGet();
        ActorRef<BuildJob> ref = system.actorOf(id, new BuildJob(id, project, task));
        pool.submit(() -> {
            int code;
            try {
                code = work.apply(new JobContext(ref));
            } catch (RuntimeException e) {
                ref.tell(j -> j.appendLine("[Error: " + e.getMessage() + "]"));
                LOG.errorf(e, "Job %s failed with exception", id);
                code = -1;
            }
            int exit = code;
            ref.tell(j -> j.finish(exit));
        });
        return id;
    }

    /** True when the build system has a command for the Docs button. */
    public boolean hasDocs(String buildSystem) {
        return command(buildSystem, "docs") != null;
    }

    /**
     * Runs the Docs command of one project in the calling thread at the lowest CPU priority,
     * so that running it for every project leaves the machine usable.
     *
     * @return the command's exit code, or -1 when it could not be run
     */
    public int runDocs(Path projectDir, String buildSystem, JobContext job, Consumer<String> out) {
        String[] cmd = command(buildSystem, "docs");
        if (cmd == null) return -1;
        String[] niced = new String[cmd.length + 3];
        niced[0] = "nice"; niced[1] = "-n"; niced[2] = "19";
        System.arraycopy(cmd, 0, niced, 3, cmd.length);
        return runCommand(niced, projectDir, out, job::started);
    }

    // ── Process runner ────────────────────────────────────────────────────────

    private void runProcess(ActorRef<BuildJob> ref, String[] cmd, Path dir, String id) {
        // Each tell() is queued; ask() from the REST thread is also queued →
        // no race condition, no volatile needed in BuildJob.
        int code = runCommand(cmd, dir, line -> ref.tell(j -> j.appendLine(line)),
                process -> ref.tell(j -> j.attach(process)));
        ref.tell(j -> j.finish(code));
        LOG.infof("Build finished: id=%s  exitCode=%d", id, code);
    }

    /**
     * Runs a command in {@code dir}, passing each output line to {@code out}, and hands the started
     * process to {@code started} so that the job can end it; returns the exit code.
     */
    private static int runCommand(String[] cmd, Path dir, Consumer<String> out, Consumer<Process> started) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            started.accept(process);
            try (BufferedReader br =
                         new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) out.accept(line);
            }
            return process.waitFor();
        } catch (IOException e) {
            out.accept("[Build error: " + e.getMessage() + "]");
            LOG.errorf(e, "Command %s failed in %s", String.join(" ", cmd), dir);
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            out.accept("[Build interrupted]");
            return -1;
        }
    }

    // ── Command table ─────────────────────────────────────────────────────────

    /** Maps (buildSystem, task) → shell command array. Returns null if unsupported. */
    private static String[] command(String buildSystem, String task) {
        if (buildSystem == null || task == null) return null;
        return switch (buildSystem) {
            case "Maven" -> switch (task) {
                case "build" -> new String[]{"bash", "-c", "rm -rf target && mvn install"};
                // aggregate puts the Javadoc of every module of a multi-module project into one
                // tree at the project root, where the Javadoc index and /docs look; for a
                // single-module project it is the same as javadoc:javadoc. doclint=none keeps a
                // dangling {@link} from stopping the whole run. disableNoFonts: plugin 3.12 passes
                // --no-fonts by default, yet the stylesheet JDK 23+ writes still imports
                // fonts/dejavu.css, so every page would ask /docs for a file that is not there.
                // The rm comes first because the plugin judges "everything is up to date" from its
                // stale-data file and skips a changed source tree, and never deletes the page of a
                // removed class.
                case "docs"  -> new String[]{"bash", "-c",
                        "rm -rf target/site/apidocs target/reports/apidocs" +
                        " target/maven-javadoc-plugin-stale-data.txt && " +
                        "JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US'" +
                        " mvn -B javadoc:aggregate -Dlocale=en_US -Ddoclint=none" +
                        " -Dmaven.javadoc.disableNoFonts=true"};
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
        private       boolean       stopRequested = false;
        /** The command the job is running now, if any. */
        private       Process       process;

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
            process  = null;
        }

        boolean stopRequested() {
            return stopRequested;
        }

        /** Records the command now running; ends it at once if a stop came first. */
        void attach(Process p) {
            process = p;
            if (stopRequested) end(p);
        }

        void stop() {
            if (!running || stopRequested) return;
            stopRequested = true;
            appendLine("[Stop requested]");
            if (process != null) end(process);
        }

        /** Ends the command and everything it started: bash → nice → mvn → javadoc. */
        private static void end(Process p) {
            p.descendants().forEach(ProcessHandle::destroy);
            p.destroy();
        }

        /** Returns a JSON-serialisable snapshot of the current state. */
        Map<String, Object> toStatus() {
            return Map.of(
                    "id",       id,
                    "project",  project,
                    "task",     task,
                    "running",  running,
                    "stopped",  stopRequested,
                    "exitCode", exitCode,
                    "output",   output.toString()
            );
        }
    }
}
