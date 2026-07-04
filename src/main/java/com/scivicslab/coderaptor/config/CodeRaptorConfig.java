package com.scivicslab.coderaptor.config;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Path;

/**
 * Application-wide configuration for code-raptor.
 * Override defaults at runtime with -D system properties:
 *   -Dcode.raptor.works-dir=/other/path
 */
@ApplicationScoped
public class CodeRaptorConfig {

    @ConfigProperty(name = "code.raptor.works-dir", defaultValue = "${user.home}/works")
    String worksDirRaw;

    /**
     * Returns the resolved works directory path.
     * Expands leading ~/ to the user home directory.
     */
    public Path getWorksDir() {
        String dir = worksDirRaw;
        if (dir.startsWith("~/")) {
            dir = System.getProperty("user.home") + dir.substring(1);
        }
        return Path.of(dir);
    }

    /**
     * Returns the directory where code-raptor stores its own data
     * (Lucene indexes, etc.). Located at <works-dir>/.code-raptor/
     */
    public Path getDataDir() {
        return getWorksDir().resolve(".code-raptor");
    }
}
