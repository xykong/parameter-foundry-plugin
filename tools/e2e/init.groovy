import com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterDefinition
import groovy.json.JsonOutput
import hudson.model.FreeStyleProject
import hudson.model.ParametersDefinitionProperty
import io.jenkins.plugins.darktheme.DarkThemeSystemManagerFactory
import io.jenkins.plugins.thememanager.ThemeManagerPageDecorator
import io.jenkins.plugins.unifiedparameters.definition.UnifiedChoiceParameterDefinition
import io.jenkins.plugins.unifiedparameters.definition.UnifiedMultiChoiceParameterDefinition
import io.jenkins.plugins.unifiedparameters.form.FormField
import io.jenkins.plugins.unifiedparameters.form.FormLayout
import io.jenkins.plugins.unifiedparameters.form.UnifiedFormParameterDefinition
import io.jenkins.plugins.unifiedparameters.migration.SourceMigrationAdapter
import io.jenkins.plugins.unifiedparameters.model.Option
import io.jenkins.plugins.unifiedparameters.provider.StaticProviderDefinition
import io.jenkins.plugins.unifiedparameters.provider.UnifiedParametersGlobalConfiguration
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def jenkins = Jenkins.get()

def themeManager = ThemeManagerPageDecorator.get()
themeManager.setTheme(new DarkThemeSystemManagerFactory())
themeManager.setDisableUserThemes(true)
themeManager.save()

def sharedEnvironments = new StaticProviderDefinition('shared-environments')
sharedEnvironments.setDisplayName('Shared environments')
sharedEnvironments.setOptions([
    new Option('development'),
    new Option('staging'),
    new Option('production'),
])
UnifiedParametersGlobalConfiguration.get().setProviders([sharedEnvironments])

def staticJob = jenkins.getItem('unified-parameters-e2e') ?: jenkins.createProject(FreeStyleProject, 'unified-parameters-e2e')
staticJob.removeProperty(ParametersDefinitionProperty)

def channel = new UnifiedChoiceParameterDefinition('CHANNEL')
channel.setChoices([new Option('staging'), new Option('production')])
channel.setRenderer('searchable')
channel.setDefaultMode('explicit')
channel.setDefaultValue('staging')

def release = new UnifiedChoiceParameterDefinition('RELEASE')
release.setChoices([new Option('stable')])
release.setRenderer('searchable')
release.setDefaultMode('explicit')
release.setDefaultValue('stable')
release.setAllowCustomValue(true)
release.setCustomValuePattern('release-[0-9]+')
release.setCustomValueMaxLength(16)

def deployMode = new UnifiedChoiceParameterDefinition('DEPLOY_MODE')
def legacyMode = new Option('legacy')
legacyMode.setDisabled(true)
legacyMode.setDisabledReason('Legacy deployment is retired')
deployMode.setChoices([new Option('safe'), new Option('fast'), legacyMode])
deployMode.setRenderer('radio')
deployMode.setDefaultMode('explicit')
deployMode.setDefaultValue('safe')

def regions = new UnifiedMultiChoiceParameterDefinition('REGIONS')
regions.setChoices([
    new Option('us-east'),
    new Option('us-west'),
    new Option('eu-central'),
    new Option('ap-southeast'),
])
regions.setRenderer('list')
regions.setDefaultMode('explicit')
regions.setDefaultValues(['us-east', 'eu-central'])

def featureA = new Option('feature-a')
featureA.setLabel('Feature A')
featureA.setGroup('Stable')
featureA.setDescription('Generally available feature')
def featureB = new Option('feature-b')
featureB.setLabel('Feature B')
featureB.setGroup('Stable')
def featureC = new Option('feature-c')
featureC.setLabel('Feature C')
featureC.setGroup('Experimental')
def featureRetired = new Option('feature-retired')
featureRetired.setLabel('Retired feature')
featureRetired.setGroup('Experimental')
featureRetired.setDisabled(true)
featureRetired.setDisabledReason('Removed from the supported release train')
def features = new UnifiedMultiChoiceParameterDefinition('FEATURES')
features.setChoices([featureA, featureB, featureC, featureRetired])
features.setRenderer('checkbox')
features.setDefaultMode('explicit')
features.setDefaultValues(['feature-a'])

def targets = new UnifiedMultiChoiceParameterDefinition('TARGETS')
targets.setChoices((0..<10000).collect { index ->
    def option = new Option(String.format('target-%04d', index))
    option.setLabel(String.format('Target %04d', index))
    option.setGroup(index < 500 ? 'Primary' : 'Secondary')
    if (index == 9998) {
        option.setLabel('部署目标 9998')
        option.setTags(['国际化', 'international', 'release'])
    }
    if (index == 9997) {
        option.setDescription('Canary deployment target')
        option.setTags(['canary', 'release'])
    }
    if (index == 9996) {
        option.setDisabled(true)
        option.setDisabledReason('Target is under maintenance')
    }
    option
})
targets.setRenderer('checkbox')
targets.setSelectedDisplay('chips')
targets.setDefaultMode('explicit')
targets.setDefaultValues(['target-0002', 'target-0001'])
targets.setMinSelections(1)
staticJob.addProperty(new ParametersDefinitionProperty(channel, release, deployMode, regions, features, targets))
staticJob.save()

def providerJob = jenkins.getItem('unified-parameters-provider-e2e') ?: jenkins.createProject(FreeStyleProject, 'unified-parameters-provider-e2e')
providerJob.removeProperty(ParametersDefinitionProperty)
def providerEnvironment = new UnifiedChoiceParameterDefinition('ENVIRONMENT')
providerEnvironment.setChoices([new Option('staging'), new Option('production')])
providerEnvironment.setRenderer('select')
providerEnvironment.setDefaultMode('explicit')
providerEnvironment.setDefaultValue('staging')
def environments = new UnifiedMultiChoiceParameterDefinition('ENVIRONMENTS')
environments.setProviderId('shared-environments')
environments.setRenderer('checkbox')
environments.setSelectedDisplay('chips')
environments.setDefaultMode('explicit')
environments.setDefaultValues(['staging'])
environments.setMinSelections(1)
def artifact = new UnifiedChoiceParameterDefinition('ARTIFACT')
artifact.setProviderId('shared-environments')
artifact.setDependsOn(['ENVIRONMENT'])
artifact.setRenderer('searchable')
artifact.setDefaultMode('first')
providerJob.addProperty(new ParametersDefinitionProperty(providerEnvironment, environments, artifact))
providerJob.save()

def formJob = jenkins.getItem('unified-parameters-form-e2e') ?: jenkins.createProject(FreeStyleProject, 'unified-parameters-form-e2e')
formJob.removeProperty(ParametersDefinitionProperty)
def serviceField = new FormField('service', 'string')
serviceField.setLabel('Service name')
serviceField.setRequired(true)
serviceField.setDefaultValue('checkout')
serviceField.setPattern('[a-z][a-z0-9-]{1,31}')
serviceField.setEnvironmentVariable('SERVICE_NAME')
def replicasField = new FormField('replicas', 'integer')
replicasField.setRequired(true)
replicasField.setDefaultValue('2')
replicasField.setMinimum(1d)
replicasField.setMaximum(20d)
def enabledField = new FormField('advanced', 'boolean')
enabledField.setLabel('Enable advanced settings')
def regionField = new FormField('region', 'choice')
regionField.setRequired(true)
regionField.setDefaultValue('us-east')
regionField.setChoices([new Option('us-east'), new Option('eu-central')])
def featuresField = new FormField('features', 'multi-choice')
featuresField.setDefaultValue('["cache"]')
featuresField.setChoices([new Option('cache'), new Option('audit'), new Option('canary')])
featuresField.setMaxItems(2)
def notesField = new FormField('notes', 'text')
notesField.setDefaultValue('review before production')
def primaryLayout = new FormLayout('primary', 'grid')
primaryLayout.setTitle('Deployment identity')
primaryLayout.setColumns(2)
primaryLayout.setFieldNames(['service', 'replicas', 'advanced', 'region'])
def advancedLayout = new FormLayout('advanced-panel', 'conditional')
advancedLayout.setTitle('Advanced settings')
advancedLayout.setFieldNames(['features', 'notes'])
advancedLayout.setVisibleWhenField('advanced')
advancedLayout.setVisibleWhenValue('true')
def deploymentForm = new UnifiedFormParameterDefinition('DEPLOYMENT')
deploymentForm.setFields([serviceField, replicasField, enabledField, regionField, featuresField, notesField])
deploymentForm.setLayouts([primaryLayout, advancedLayout])
deploymentForm.setOutputMode('map')
deploymentForm.setEnvironmentExport('json')
formJob.addProperty(new ParametersDefinitionProperty(deploymentForm))
formJob.save()

def configJob = jenkins.getItem('up-config-e2e') ?: jenkins.createProject(FreeStyleProject, 'up-config-e2e')
configJob.removeProperty(ParametersDefinitionProperty)
def configTargets = new UnifiedMultiChoiceParameterDefinition('CONFIG_TARGETS')
configTargets.setChoices([new Option('android'), new Option('ios')])
configTargets.setRenderer('checkbox')
configTargets.setSelectedDisplay('chips')
configTargets.setDefaultMode('explicit')
configTargets.setDefaultValues(['android'])
configJob.addProperty(new ParametersDefinitionProperty(configTargets))
configJob.save()

def pipeline = jenkins.getItem('unified-parameters-pipeline-e2e') ?: jenkins.createProject(WorkflowJob, 'unified-parameters-pipeline-e2e')
pipeline.removeProperty(ParametersDefinitionProperty)
pipeline.addProperty(new ParametersDefinitionProperty(targets))
pipeline.setDefinition(new CpsFlowDefinition(
    '''echo "TARGETS=${params.TARGETS.join('|')}"''',
    true
))
pipeline.save()

def source = new ExtendedChoiceParameterDefinition(
    'LEGACY_TARGETS',
    ExtendedChoiceParameterDefinition.PARAMETER_TYPE_CHECK_BOX,
    'android,ios,web',
    null, null, null, null, null, null, null,
    'android,ios',
    null, null, null, null, null, null, null, null, null, null, null, null, null,
    null, null, false, false, 5, 'Synthetic source', ','
)
def adapter = SourceMigrationAdapter.all().find { it.sourcePluginId == 'extended-choice-parameter' }
def plan = adapter.inspect(source)
def migrationJob = jenkins.getItem('unified-parameters-migration-e2e') ?: jenkins.createProject(FreeStyleProject, 'unified-parameters-migration-e2e')
migrationJob.removeProperty(ParametersDefinitionProperty)
migrationJob.addProperty(new ParametersDefinitionProperty(source))
migrationJob.save()
new File(jenkins.rootDir, 'migration-plan.json').text = JsonOutput.prettyPrint(JsonOutput.toJson([
    classification: plan.classification.name(),
    targetClass: plan.target?.class?.name,
    decisions: plan.decisions,
]))

new File(jenkins.rootDir, 'unified-parameters-e2e-ready').text = 'ready'
