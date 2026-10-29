package io.jenkins.plugins.parameterfoundry.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition;
import io.jenkins.plugins.parameterfoundry.model.Option;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;

class MigrationDiffsTest {

    @Test
    void keepsAProductionSizedChoiceDiffReviewable() {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            values.add("platform-" + index);
        }
        ExtendedChoiceParameterDefinition source = new ExtendedChoiceParameterDefinition(
                "PLATFORMS",
                ExtendedChoiceParameterDefinition.PARAMETER_TYPE_MULTI_SELECT,
                String.join(",", values),
                values.get(0),
                null);
        FoundryChoiceParameterDefinition target = new FoundryChoiceParameterDefinition("PLATFORMS");
        target.setSelectionMode("multiple");
        target.setRenderer("checkbox");
        target.setChoices(values.stream().map(Option::new).toList());
        target.setDefaultMode("explicit");
        target.setDefaultValues(List.of(values.get(0)));

        List<String> readableTarget = MigrationDiffs.readable(Jenkins.XSTREAM2.toXML(target));

        assertTrue(readableTarget.size() < 100, "options must collapse to one line each: " + readableTarget.size());
        assertTrue(
                readableTarget.stream()
                        .anyMatch(line -> line.contains("<io.jenkins.plugins.parameterfoundry.model.Option>")
                                && line.contains("platform-39")),
                readableTarget.toString());

        String diff = MigrationDiffs.definitionDiff(source, target);

        assertTrue(diff.contains("- <com.cwctravel"), firstLines(diff));
        assertTrue(
                diff.contains("+ <io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition>"),
                firstLines(diff));
        assertTrue(diff.contains("<io.jenkins.plugins.parameterfoundry.model.Option>"), firstLines(diff));
        assertTrue(diff.contains("platform-39"), firstLines(diff));
        assertTrue(
                diff.lines().count() < 200,
                "diff must stay reviewable: " + diff.lines().count());
    }

    @Test
    void condensesLongUnchangedRunsAndKeepsBothSides() {
        String diff = MigrationDiffs.foundry(
                List.of("a", "b", "c", "d", "e", "f", "g", "h", "x"),
                List.of("a", "b", "c", "d", "e", "f", "g", "h", "y"));

        assertTrue(diff.contains("unchanged line(s)"), diff);
        assertTrue(diff.contains("- x"), diff);
        assertTrue(diff.contains("+ y"), diff);
    }

    private static String firstLines(String diff) {
        return diff.lines().limit(20).reduce("", (left, right) -> left + "\n" + right);
    }
}
