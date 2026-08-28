import hudson.model.*
import hudson.tasks.Shell
import org.jenkinsci.plugins.uiparameter.*
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def definitions = {
    def parameter = new HtmlFormParameterDefinition('DEPLOY_FORM')
    parameter.setDescription('Sanitized composite deployment form.')
    parameter.setTemplateHtml('''<div class="html-parameters-card">
  <h3>Deploy bundle</h3>
  <label>Branch <input id="html-parameters-branch" class="jenkins-input" value="main"></label>
  <label>Environment <select id="html-parameters-env" class="jenkins-select__input">
    <option value="dev">Development</option>
    <option value="staging" selected>Staging</option>
    <option value="prod">Production</option>
  </select></label>
  <label><input id="html-parameters-tests" type="checkbox" checked> Run smoke tests</label>
</div>''')
    parameter.setCustomCss('''.html-parameters-card {
  display:grid; gap:14px; padding:20px; border:1px solid var(--input-border);
  border-radius:12px; background:var(--card-background);
}
.html-parameters-card label { display:grid; gap:6px; }''')
    parameter.setMappings([
        new HtmlFormMapping('BRANCH', 'html-parameters-branch'),
        new HtmlFormMapping('ENVIRONMENT', 'html-parameters-env'),
        new HtmlFormMapping('RUN_TESTS', 'html-parameters-tests'),
    ])
    [parameter]
}
def freestyle = jenkins.createProject(FreeStyleProject.class, 'ui-lab-freestyle')
freestyle.addProperty(new ParametersDefinitionProperty(definitions()))
freestyle.buildersList.add(new Shell('env | sort'))
freestyle.save()
def pipeline = jenkins.createProject(WorkflowJob.class, 'ui-lab-pipeline')
pipeline.addProperty(new ParametersDefinitionProperty(definitions()))
pipeline.definition = new CpsFlowDefinition('echo "${env.BRANCH} ${env.ENVIRONMENT} ${env.RUN_TESTS}"', true)
pipeline.save()
