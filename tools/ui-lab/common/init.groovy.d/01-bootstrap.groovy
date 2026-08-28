import hudson.security.AuthorizationStrategy
import hudson.security.SecurityRealm
import jenkins.model.Jenkins
import jenkins.model.JenkinsLocationConfiguration

def instance = Jenkins.get()
instance.setSecurityRealm(SecurityRealm.NO_AUTHENTICATION)
instance.setAuthorizationStrategy(AuthorizationStrategy.UNSECURED)
instance.setNumExecutors(2)
instance.setSystemMessage('Isolated Jenkins Parameter Plugin UI Lab')
instance.save()

def location = JenkinsLocationConfiguration.get()
location.setUrl('http://127.0.0.1:18080/')
location.save()

def profile = System.getenv('LAB_PROFILE')
if (!profile) {
    throw new IllegalStateException('LAB_PROFILE is required')
}

def seedFile = new File('/opt/parameter-lab/profile/seed.groovy')
if (!seedFile.isFile()) {
    throw new IllegalStateException("Missing profile seed: ${seedFile}")
}

def binding = new Binding([
    jenkins: instance,
    profile: profile,
])
def shell = new GroovyShell(instance.pluginManager.uberClassLoader, binding)
shell.evaluate(seedFile)

def ready = new File(instance.rootDir, 'ui-lab-ready')
ready.text = "${profile}\n"
println("UI_LAB_READY|${profile}")
