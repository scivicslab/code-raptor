package com.scivicslab.coderaptor.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * E2E tests for symbol search → code-server file navigation.
 *
 * <p>Tests run in document order against an already-running environment.
 */
public class SymbolSearchE2ETest {

    private static final String CODE_RAPTOR  = "http://localhost:4000";
    private static final String CODE_SERVER  = "http://localhost:8080";
    private static final String TEST_SYMBOL  = "tell";   // exists in POJO-actor
    private static final int    TIMEOUT_MS   = 15_000;
    private static final int    IDE_TIMEOUT  = 45_000;   // code-server may be slow on first load

    // ── Test 1 ─────────────────────────────────────────────────────────────

    /**
     * Verify that searching a known symbol returns at least one result row.
     */
    public void symbolSearch_returnsResults() {
        try (Playwright pw = Playwright.create()) {
            Page page = launchPage(pw);
            page.navigate(CODE_RAPTOR);

            fillAndSearch(page, TEST_SYMBOL, "references");

            int count = page.locator(".result-line").count();
            assertTrue(count > 0,
                "Expected at least 1 symbol result for '" + TEST_SYMBOL + "', got " + count);

            System.out.printf("    → %d result(s) found%n", count);
        }
    }

    // ── Test 2 ─────────────────────────────────────────────────────────────

    /**
     * Verify that each result's file link contains a {@code payload} query parameter
     * with a JSON array that includes {@code openFile} (vscode-remote URI + line) and
     * {@code gotoLineMode}.
     */
    public void symbolSearch_fileLinkHasGotoParam() {
        try (Playwright pw = Playwright.create()) {
            Page page = launchPage(pw);
            page.navigate(CODE_RAPTOR);

            fillAndSearch(page, TEST_SYMBOL, "references");

            Locator firstLink = page.locator("a.file-path").first();
            String href = firstLink.getAttribute("href");
            System.out.printf("    → href: %s%n", href);

            assertTrue(href != null && !href.isBlank(), "File link has no href");
            assertTrue(href.contains("payload="),
                "Expected 'payload=' in href but got: " + href);

            // Decode and parse payload JSON
            String payloadRaw = extractQueryParam(href, "payload");
            String payloadJson = URLDecoder.decode(payloadRaw, StandardCharsets.UTF_8);
            System.out.printf("    → payload (decoded): %s%n", payloadJson);

            // Must be a JSON array: [["openFile","vscode-remote://host/path:line"],["gotoLineMode","true"]]
            assertTrue(payloadJson.contains("\"openFile\""),
                "payload JSON should contain 'openFile' key: " + payloadJson);
            assertTrue(payloadJson.contains("\"gotoLineMode\""),
                "payload JSON should contain 'gotoLineMode' key: " + payloadJson);
            assertTrue(payloadJson.contains("vscode-remote://"),
                "openFile URI should use vscode-remote:// scheme: " + payloadJson);
        }
    }

    // ── Test 3 ─────────────────────────────────────────────────────────────

    /**
     * Verify that clicking a result file link opens code-server and navigates
     * to the expected line.
     *
     * <p>The expected line is extracted from the {@code goto} URL parameter.
     * After code-server loads, the VS Code status bar is checked for
     * "Ln &lt;line&gt;" to confirm the cursor landed on the correct line.
     */
    public void symbolSearch_gotoOpensFileAtLine() {
        try (Playwright pw = Playwright.create()) {
            // Step 1: get the href from code-raptor search results
            Page raptorPage = launchPage(pw);
            raptorPage.navigate(CODE_RAPTOR);
            fillAndSearch(raptorPage, TEST_SYMBOL, "references");

            Locator firstLink   = raptorPage.locator("a.file-path").first();
            String href         = firstLink.getAttribute("href");
            String payloadRaw   = extractQueryParam(href, "payload");
            String payloadJson  = URLDecoder.decode(payloadRaw, StandardCharsets.UTF_8);
            // payloadJson = [["openFile","vscode-remote://host/path/File.java:52"],["gotoLineMode","true"]]
            // Extract the openFile URI from the JSON array (simple regex, no full JSON parse needed)
            Matcher openFileMatcher = Pattern.compile("\"openFile\"\\s*,\\s*\"([^\"]+)\"").matcher(payloadJson);
            if (!openFileMatcher.find()) throw new AssertionError("openFile not found in payload: " + payloadJson);
            String openFileUri  = openFileMatcher.group(1);
            int    expected     = parseLineFromGoto(openFileUri);  // same :line suffix pattern
            System.out.printf("    → expected line : %d  (openFile=%s)%n", expected, openFileUri);
            System.out.printf("    → opening URL   : %s%n", href);

            // Step 2: open code-server directly — simpler than waitForPage, avoids SPA tab reuse issues
            Browser ideBrowser = pw.chromium()
                    .launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page ideTab = ideBrowser.newContext().newPage();
            ideTab.navigate(href);

            ideTab.waitForLoadState(
                    com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(IDE_TIMEOUT));

            System.out.printf("    → IDE title     : %s%n", ideTab.title());
            System.out.printf("    → IDE final URL : %s%n", ideTab.url());

            // Step 3: wait for actual text editor to render
            // .view-lines is only present when Monaco has rendered file content;
            // it does NOT appear in chat/overflow Monaco widgets.
            ideTab.waitForSelector(".monaco-editor .view-lines",
                    new Page.WaitForSelectorOptions().setTimeout(IDE_TIMEOUT));
            System.out.println("    → file editor visible (.view-lines)");

            // Step 4: let VS Code settle cursor positioning after file opens
            ideTab.waitForTimeout(2000);

            // Step 5: read status bar
            String statusText = readStatusBarLine(ideTab);
            System.out.printf("    → status bar    : '%s'%n", statusText);

            int actual = parseLineFromStatus(statusText);
            System.out.printf("    → actual line   : %d%n", actual);

            assertTrue(actual == expected,
                "Expected cursor at line " + expected + " but status bar shows: " + statusText);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Page launchPage(Playwright pw) {
        Browser browser = pw.chromium()
                .launch(new BrowserType.LaunchOptions().setHeadless(true));
        return browser.newContext().newPage();
    }

    private void fillAndSearch(Page page, String symbol, String type) {
        page.fill("#symbol-input", symbol);
        page.selectOption("#symbol-type", type);
        page.click("button.btn-primary");
        // Button navigates to /search page; wait for client-side results to render
        page.waitForSelector("a.file-path",
                new Page.WaitForSelectorOptions().setTimeout(TIMEOUT_MS));
    }

    /** Extracts a single query parameter value from a URL string. */
    private String extractQueryParam(String url, String name) {
        Matcher m = Pattern.compile("[?&]" + Pattern.quote(name) + "=([^&]+)").matcher(url);
        if (!m.find()) throw new AssertionError("Query param '" + name + "' not found in: " + url);
        return m.group(1);
    }

    /** Parses the line number from a goto value like "path/File.java:42" or "path/File.java:42:1". */
    private int parseLineFromGoto(String gotoVal) {
        Matcher m = Pattern.compile(":(\\d+)(?::\\d+)?$").matcher(gotoVal);
        if (!m.find()) throw new AssertionError("Cannot parse line from goto: " + gotoVal);
        return Integer.parseInt(m.group(1));
    }

    /**
     * Reads the cursor-position text from the VS Code status bar.
     * Tries several selectors that different VS Code / code-server versions use.
     * Also dumps all status-bar items for diagnosis if none match.
     */
    private String readStatusBarLine(Page page) {
        // Try each known status-bar selector in order
        String[] selectors = {
            "[id='status.editor.selection']",
            ".statusbar-item[title*='Ln']",
            "a[title*='Ln ']",
            ".editor-statusbar-item",
        };
        for (String sel : selectors) {
            try {
                Locator loc = page.locator(sel).first();
                loc.waitFor(new Locator.WaitForOptions().setTimeout(5000));
                String text = loc.textContent();
                if (text != null && text.contains("Ln")) {
                    System.out.printf("    → matched selector: %s%n", sel);
                    return text.trim();
                }
            } catch (Exception ignored) {}
        }
        // Fallback: dump entire status bar content for diagnosis
        try {
            String all = page.locator(".statusbar").first().textContent().trim();
            System.out.printf("    → full status bar dump: '%s'%n", all);
            return all;
        } catch (Exception e) {
            // Dump all visible text around status bar area
            try {
                int count = page.locator(".statusbar-item").count();
                System.out.printf("    → statusbar-item count: %d%n", count);
                for (int i = 0; i < Math.min(count, 10); i++) {
                    String t = page.locator(".statusbar-item").nth(i).textContent();
                    System.out.printf("    →   [%d] '%s'%n", i, t);
                }
            } catch (Exception ignored) {}
            return "(status bar not found)";
        }
    }

    /** Parses line number from status bar text like "Ln 42, Col 1". */
    private int parseLineFromStatus(String status) {
        Matcher m = Pattern.compile("Ln\\s+(\\d+)").matcher(status);
        if (!m.find()) throw new AssertionError("Cannot parse line from status: " + status);
        return Integer.parseInt(m.group(1));
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
