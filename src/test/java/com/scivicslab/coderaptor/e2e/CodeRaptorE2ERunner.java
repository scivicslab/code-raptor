package com.scivicslab.coderaptor.e2e;

/**
 * E2E test runner for code-raptor.
 *
 * <p>Run against an already-running environment:
 * <pre>
 *   mvn test-compile exec:java -Dexec.mainClass=com.scivicslab.coderaptor.e2e.CodeRaptorE2ERunner \
 *       -Dexec.classpathScope=test
 * </pre>
 *
 * <p>Prerequisites:
 * <ul>
 *   <li>code-raptor running on localhost:4000</li>
 *   <li>code-server  running on localhost:8080</li>
 *   <li>Playwright Chromium installed (run once: see below)</li>
 * </ul>
 *
 * <p>Install Chromium (once):
 * <pre>
 *   mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI \
 *       -Dexec.args="install --with-deps chromium" -Dexec.classpathScope=test
 * </pre>
 */
public class CodeRaptorE2ERunner {

    public static void main(String[] args) {
        System.out.println("=== code-raptor E2E ===");

        int failures = 0;
        failures += run("SymbolSearch_returnsResults",         new SymbolSearchE2ETest()::symbolSearch_returnsResults);
        failures += run("SymbolSearch_fileLinkHasGotoParam",   new SymbolSearchE2ETest()::symbolSearch_fileLinkHasGotoParam);
        failures += run("SymbolSearch_gotoOpensFileAtLine",    new SymbolSearchE2ETest()::symbolSearch_gotoOpensFileAtLine);

        System.out.println("\n=== " + (failures == 0 ? "ALL PASSED" : failures + " FAILED") + " ===");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static int run(String name, Runnable test) {
        System.out.print("  " + name + " ... ");
        try {
            test.run();
            System.out.println("PASS");
            return 0;
        } catch (AssertionError | Exception e) {
            System.out.println("FAIL: " + e.getMessage());
            return 1;
        }
    }
}
