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

    private static final int MAX_MATRIX_LINES = 400;
    private static final int MAX_OUTPUT_LINES = 400;
    private static final int CONTEXT_LINES = 2;

    private MigrationDiffs() {}

    public static String definitionDiff(ParameterDefinition source, ParameterDefinition target) {
        if (source == null || target == null) {
            return "";
        }
        return unified(readable(Jenkins.XSTREAM2.toXML(source)), readable(Jenkins.XSTREAM2.toXML(target)));
    }

    static String unified(List<String> before, List<String> after) {
        if (before.size() > MAX_MATRIX_LINES || after.size() > MAX_MATRIX_LINES) {
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

    private static List<String> readable(String xml) {
        String normalized = xml.replace("\r\n", "\n").replace("><", ">\n<");
        return List.of(normalized.split("\n", -1));
    }
}
