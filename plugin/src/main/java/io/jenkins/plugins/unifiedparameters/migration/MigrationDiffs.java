package io.jenkins.plugins.unifiedparameters.migration;

import hudson.model.ParameterDefinition;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;

/**
 * Line-oriented diff of two serialized parameter definitions. The batch preview uses it to show
 * the exact configuration change before anything is written to the Job.
 */
public final class MigrationDiffs {

    /** Bounds the LCS matrix so a pathological definition cannot exhaust the controller. */
    private static final long MAX_MATRIX_CELLS = 400_000L;

    private static final int MAX_LINES = 5_000;
    private static final int MAX_OUTPUT_LINES = 400;
    private static final int CONTEXT_LINES = 2;
    private static final int MAX_INLINE_ELEMENT_LENGTH = 400;

    private MigrationDiffs() {}

    public static String definitionDiff(ParameterDefinition source, ParameterDefinition target) {
        if (source == null || target == null) {
            return "";
        }
        return unified(readable(Jenkins.XSTREAM2.toXML(source)), readable(Jenkins.XSTREAM2.toXML(target)));
    }

    static String unified(List<String> before, List<String> after) {
        if (before.size() > MAX_LINES
                || after.size() > MAX_LINES
                || (long) before.size() * after.size() > MAX_MATRIX_CELLS) {
            return "... definition too large for an inline diff (" + before.size() + " lines before, " + after.size()
                    + " lines after)";
        }
        int[][] lengths = new int[before.size() + 1][after.size() + 1];
        for (int i = before.size() - 1; i >= 0; i--) {
            for (int j = after.size() - 1; j >= 0; j--) {
                lengths[i][j] = before.get(i).equals(after.get(j))
                        ? lengths[i + 1][j + 1] + 1
                        : Math.max(lengths[i + 1][j], lengths[i][j + 1]);
            }
        }
        List<String> lines = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < before.size() && j < after.size()) {
            if (before.get(i).equals(after.get(j))) {
                lines.add("  " + before.get(i));
                i++;
                j++;
            } else if (lengths[i + 1][j] >= lengths[i][j + 1]) {
                lines.add("- " + before.get(i++));
            } else {
                lines.add("+ " + after.get(j++));
            }
        }
        while (i < before.size()) {
            lines.add("- " + before.get(i++));
        }
        while (j < after.size()) {
            lines.add("+ " + after.get(j++));
        }
        return condense(lines);
    }

    private static String condense(List<String> lines) {
        List<String> kept = new ArrayList<>();
        int index = 0;
        while (index < lines.size()) {
            if (!isContext(lines.get(index))) {
                kept.add(lines.get(index++));
                continue;
            }
            int start = index;
            while (index < lines.size() && isContext(lines.get(index))) {
                index++;
            }
            int end = index;
            boolean changedBefore = start > 0;
            boolean changedAfter = end < lines.size();
            int keepStart = changedBefore ? Math.max(start, end - CONTEXT_LINES) : start;
            int keepEnd = changedAfter ? Math.min(end, start + CONTEXT_LINES) : end;
            for (int position = keepStart; position < keepEnd; position++) {
                kept.add(lines.get(position));
            }
            int skipped = keepEnd - keepStart;
            if (end - start - skipped > 0) {
                kept.add("... " + (end - start - skipped) + " unchanged line(s)");
            }
        }
        if (kept.size() > MAX_OUTPUT_LINES) {
            List<String> truncated = new ArrayList<>(kept.subList(0, MAX_OUTPUT_LINES));
            truncated.add("... " + (kept.size() - MAX_OUTPUT_LINES) + " more line(s) omitted");
            kept = truncated;
        }
        return String.join("\n", kept);
    }

    private static boolean isContext(String line) {
        return line.startsWith("  ");
    }

    /**
     * Renders the serialized definition as one line per element, keeping an element whose children
     * are all values on a single line. A migrated choice option therefore reads as one line instead
     * of one line per field, which is what makes the diff reviewable for production-sized lists.
     */
    static List<String> readable(String xml) {
        List<String> units = tokenize(xml.replace("\r\n", "\n"));
        List<String> lines = new ArrayList<>();
        int[] index = {0};
        while (index[0] < units.size()) {
            if (isElement(units.get(index[0]))) {
                printElement(units, index, 0, lines);
            } else {
                index[0]++;
            }
        }
        return List.copyOf(lines);
    }

    private static void printElement(List<String> units, int[] index, int depth, List<String> lines) {
        String open = units.get(index[0]);
        if (open.endsWith("/>")) {
            lines.add(indent(depth) + open);
            index[0]++;
            return;
        }
        int close = index[0] + 1;
        int nesting = 0;
        boolean nested = false;
        while (close < units.size()) {
            String unit = units.get(close);
            if (isStartTag(unit)) {
                if (nesting >= 1) {
                    nested = true;
                }
                nesting++;
            } else if (isEndTag(unit)) {
                if (nesting == 0) {
                    break;
                }
                nesting--;
            }
            close++;
        }
        boolean closed = close < units.size();
        String closeTag = closed ? units.get(close) : "";
        StringBuilder inline = new StringBuilder(indent(depth)).append(open);
        for (int position = index[0] + 1; position < close; position++) {
            inline.append(units.get(position));
        }
        if (!nested && inline.length() + closeTag.length() <= MAX_INLINE_ELEMENT_LENGTH) {
            lines.add(inline.append(closeTag).toString());
            index[0] = close + 1;
            return;
        }
        lines.add(indent(depth) + open);
        int[] child = {index[0] + 1};
        while (child[0] < close) {
            if (isElement(units.get(child[0])) && !isEndTag(units.get(child[0]))) {
                printElement(units, child, depth + 1, lines);
            } else {
                child[0]++;
            }
        }
        if (closed) {
            lines.add(indent(depth) + closeTag);
        }
        index[0] = close + 1;
    }

    private static boolean isStartTag(String unit) {
        return unit.startsWith("<") && !unit.startsWith("</") && !unit.endsWith("/>");
    }

    private static boolean isEndTag(String unit) {
        return unit.startsWith("</");
    }

    private static boolean isElement(String unit) {
        return unit.startsWith("<");
    }

    private static String indent(int depth) {
        return "  ".repeat(Math.max(0, depth));
    }

    /** Splits the XML into tags and text so the printer never has to parse attributes. */
    private static List<String> tokenize(String xml) {
        List<String> units = new ArrayList<>();
        int position = 0;
        while (position < xml.length()) {
            int open = xml.indexOf('<', position);
            if (open < 0) {
                addText(units, xml.substring(position));
                break;
            }
            if (open > position) {
                addText(units, xml.substring(position, open));
            }
            int close = xml.indexOf('>', open);
            if (close < 0) {
                addText(units, xml.substring(open));
                break;
            }
            units.add(xml.substring(open, close + 1));
            position = close + 1;
        }
        return units;
    }

    private static void addText(List<String> units, String text) {
        if (!text.isBlank()) {
            units.add(text);
        }
    }
}
