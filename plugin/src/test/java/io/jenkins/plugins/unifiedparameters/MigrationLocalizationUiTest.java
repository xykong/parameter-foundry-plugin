package io.jenkins.plugins.unifiedparameters;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterDefinition;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import java.util.Locale;
import org.biouno.unochoice.ChoiceParameter;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class MigrationLocalizationUiTest {

    @Test
    void migrationPageAndRetirementDashboardRenderChinese(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("localized-migration");
        project.addProperty(new ParametersDefinitionProperty(multipleWithEmptyOption(), literalActiveChoice()));

        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        try (var client =
                jenkins.createWebClient().withJavaScriptEnabled(false).withThrowExceptionOnFailingStatusCode(false)) {
            client.addRequestHeader("Accept-Language", "zh-CN,zh;q=0.9");

            HtmlPage migration = client.getPage(project, "unified-parameters/");
            String text = migration.asNormalizedText();

            assertTrue(text.contains("统一参数迁移预览"), text.substring(0, Math.min(300, text.length())));
            assertTrue(text.contains("原子批量迁移"));
            assertTrue(text.contains("迁移前需要确认的决策"));
            assertTrue(text.contains("将源中的空选项替换为可选的 None 行为或显式的哨兵值。"), "decision not translated");
            assertTrue(text.contains("显式取值以及默认/未传值输入"), "verification cases not translated");
            assertTrue(text.contains("源选项数"), "fact labels not translated");
            assertFalse(text.contains("!Migration"), "unresolved message key in the page");

            HtmlPage retirement = client.goTo("unified-parameters-retirement/");
            String dashboard = retirement.asNormalizedText();
            assertTrue(dashboard.contains("统一参数退役看板"), dashboard.substring(0, Math.min(300, dashboard.length())));
            assertTrue(dashboard.contains("未扫描的范围"));
            assertFalse(dashboard.contains("!Retirement"), "unresolved message key in the dashboard");
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static ExtendedChoiceParameterDefinition multipleWithEmptyOption() {
        ExtendedChoiceParameterDefinition source = new ExtendedChoiceParameterDefinition(
                "LEGACY_TARGETS",
                ExtendedChoiceParameterDefinition.PARAMETER_TYPE_CHECK_BOX,
                "android,,web",
                "android",
                null);
        source.setDescription("Synthetic source");
        return source;
    }

    private static ParameterDefinition literalActiveChoice() {
        ChoiceParameter source = new ChoiceParameter("CHANNEL");
        source.setScript("return ['staging', 'production:selected']");
        return source;
    }
}
