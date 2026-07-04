package com.scivicslab.coderaptor.model;

/**
 * A single hit from a global symbol search.
 *
 * @param symbol  the matched symbol name
 * @param line    line number in the source file
 * @param file    absolute path to the source file
 * @param content the source line that contains the symbol
 */
public record SymbolHit(String symbol, int line, String file, String content) {

    /**
     * Parse one line of global output into a SymbolHit.
     *
     * <p>global output format (space-padded columns):
     * {@code addIIActor       110 /path/to/File.java   system.addIIActor(loaderActor);}
     *
     * @param line a single line from global stdout
     * @return parsed SymbolHit, or null if the line cannot be parsed
     */
    public static SymbolHit parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        // Split on whitespace: [symbol, lineNo, filePath, ...rest (source content)]
        String[] parts = line.split("\\s+", 4);
        if (parts.length < 4) {
            return null;
        }
        try {
            String symbol = parts[0];
            int lineNo = Integer.parseInt(parts[1]);
            String file = parts[2];
            String content = parts[3];
            return new SymbolHit(symbol, lineNo, file, content);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
