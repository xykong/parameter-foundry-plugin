import jenkins.model.Jenkins
import hudson.model.*
import io.jenkins.plugins.parameterfoundry.provider.*
import io.jenkins.plugins.parameterfoundry.definition.FoundryChoiceParameterDefinition
import io.jenkins.plugins.parameterfoundry.model.Option
import groovy.json.JsonOutput
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.InetAddress
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.security.MessageDigest

def j=Jenkins.get()
j.securityRealm=hudson.security.SecurityRealm.NO_AUTHENTICATION
j.authorizationStrategy=hudson.security.AuthorizationStrategy.UNSECURED
j.numExecutors=0
jenkins.model.JenkinsLocationConfiguration.get().url=System.getenv('PF_LAB_URL')
assert Jenkins.VERSION=='2.568.3'
assert java.security.Security.getProperty('networkaddress.cache.ttl')=='0'
assert java.security.Security.getProperty('networkaddress.cache.negative.ttl')=='0'
assert j.pluginManager.failedPlugins.isEmpty()
def hash={byte[] bytes->MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()}
assert hash(j.pluginManager.getPlugin('parameter-foundry').archive.bytes)==System.getenv('PF_LAB_HPI_SHA')
def state=new AtomicReference<String>('topic/initial\n')
def status=new AtomicInteger(200)
def requests=new AtomicInteger()
def mock=HttpServer.create(new InetSocketAddress('0.0.0.0',18182),0)
mock.executor=Executors.newCachedThreadPool({Runnable r->def t=new Thread(r,'raw-rest-synthetic-peer');t.daemon=true;t} as java.util.concurrent.ThreadFactory)
def query={exchange->(exchange.requestURI.rawQuery?:'').split('&').findAll{it}.collectEntries{pair->def p=pair.split('=',2);[(URLDecoder.decode(p[0],'UTF-8')):URLDecoder.decode(p.length==2?p[1]:'','UTF-8')]}}
def respond={exchange,Map data->byte[] bytes=JsonOutput.toJson(data).getBytes('UTF-8');exchange.responseHeaders.set('Content-Type','application/json');exchange.sendResponseHeaders(200,bytes.length);exchange.responseBody.write(bytes);exchange.close()}
mock.createContext('/api/value',{exchange->
 requests.incrementAndGet();int code=status.get();byte[] bytes=(code<400?state.get():'synthetic-error-body-not-a-choice').getBytes('UTF-8')
 exchange.responseHeaders.set('Content-Type','text/plain; charset=ISO-8859-1');exchange.sendResponseHeaders(code,bytes.length==0?-1:bytes.length)
 if(bytes.length>0 && code!=304)exchange.responseBody.write(bytes)
 exchange.close()
} as com.sun.net.httpserver.HttpHandler)
mock.createContext('/control',{exchange->
 assert exchange.requestMethod=='POST';def q=query(exchange);if(q.containsKey('body'))state.set(q.body);if(q.containsKey('status')){int code=q.status as int;assert code in [200,302,304,306,308,503];status.set(code)};respond(exchange,[status:status.get(),body:state.get()])
} as com.sun.net.httpserver.HttpHandler)
mock.createContext('/native-default',{exchange->
 def name=query(exchange).job;assert name in ['raw-rest-values','raw-rest-fallback','raw-rest-dns','raw-rest-tls','raw-tls-denied','status-old','status-body'];def job=j.getItemByFullName(name);def definition=job.getProperty(ParametersDefinitionProperty).getParameterDefinition('REF');def value=definition.defaultParameterValue
 def diagnostic=null
 if(value==null && name!='raw-tls-denied'){
  def health=ProviderRuntime.get().health('jenkins',name).orElse(null)
  diagnostic=[healthy:health?.healthy(),message:health?.message(),latencyMillis:health?.latencyMillis()]
  try{ProviderResolutionService.resolve(job,definition,ProviderOperation.RESOLVE,[:])}
  catch(io.jenkins.plugins.parameterfoundry.evaluation.EvaluationException failure){diagnostic.code=failure.code.toString()}
 }
 respond(exchange,[value:value?.value,type:value?.class?.name,rawStatusPolicy:ParameterFoundryGlobalConfiguration.get().providers.find{it.id==name}.rawStatusPolicy,diagnostic:diagnostic])
} as com.sun.net.httpserver.HttpHandler)
// Observe the owned runtime's real budget; never reset it or increase its limits.
def field={Object owner,String name->def declared=owner.getClass().getDeclaredField(name);declared.accessible=true;declared.get(owner)}
mock.createContext('/rate-budget',{exchange->
 def limiter=field(ProviderRuntime.get(),'limiter');def windows=field(limiter,'windows');long now=System.currentTimeMillis();long minute=now.intdiv(60000)
 def counts=[]
 windows.each{key,window->if(key.startsWith('principal:')){synchronized(window){if(field(window,'minute')==minute)counts.add(field(window,'count'))}}}
 respond(exchange,[principalLimit:field(limiter,'principalRate'),maximumPrincipalCount:counts?counts.max():0,waitMillis:60000-now%60000+250])
} as com.sun.net.httpserver.HttpHandler)
mock.createContext('/stats',{exchange->assert j.queue.items.length==0;assert j.getAllItems(Job).every{it.nextBuildNumber==1};respond(exchange,[requests:requests.get(),jobs:j.getAllItems(Job).size(),queue:0,builds:0])} as com.sun.net.httpserver.HttpHandler)
mock.start()
def address=InetAddress.localHost.hostAddress
assert !InetAddress.getByName(address).isLoopbackAddress()
String peer='http://'+address+':18182/api/'
def profile=new ConnectionProfile('synthetic-raw-peer',peer)
profile.allowedPathPrefixes=['/api/'];profile.allowPrivateAddresses=true;profile.useJenkinsProxy=false
profile.connectTimeoutSeconds=2;profile.readTimeoutSeconds=2;profile.totalTimeoutSeconds=5
def dnsProfile=new ConnectionProfile('synthetic-raw-dns','http://raw-rest-dns.invalid:18182/api/')
dnsProfile.allowedPathPrefixes=['/api/'];dnsProfile.allowPrivateAddresses=true;dnsProfile.useJenkinsProxy=false
dnsProfile.connectTimeoutSeconds=2;dnsProfile.readTimeoutSeconds=2;dnsProfile.totalTimeoutSeconds=5
def tlsProfile=new ConnectionProfile('synthetic-raw-tls','https://raw-rest-tls.invalid:18183/api/')
tlsProfile.allowedPathPrefixes=['/api/'];tlsProfile.allowPrivateAddresses=true;tlsProfile.useJenkinsProxy=false
tlsProfile.connectTimeoutSeconds=2;tlsProfile.readTimeoutSeconds=2;tlsProfile.totalTimeoutSeconds=5
def keys=java.security.KeyStore.getInstance('PKCS12');new File('/opt/pf-lab/peer.p12').withInputStream{keys.load(it,'owned-fixture-pass'.toCharArray())}
def managers=javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.defaultAlgorithm);managers.init(keys,'owned-fixture-pass'.toCharArray())
def tls=javax.net.ssl.SSLContext.getInstance('TLS');tls.init(managers.keyManagers,null,null)
def tlsMock=com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress('0.0.0.0',18183),0);tlsMock.httpsConfigurator=new com.sun.net.httpserver.HttpsConfigurator(tls);tlsMock.executor=mock.executor
tlsMock.createContext('/api/value',{exchange->requests.incrementAndGet();byte[] bytes=state.get().getBytes('UTF-8');exchange.sendResponseHeaders(200,bytes.length);exchange.responseBody.write(bytes);exchange.close()} as com.sun.net.httpserver.HttpHandler);tlsMock.start()
def tlsMessage='(certificate_unknown) No subject alternative DNS name matching raw-rest-tls.invalid found.'
def tlsFingerprint=hash(('tls-rejected-v1\0javax.net.ssl.SSLHandshakeException\0'+tlsMessage).getBytes('UTF-8'))
ParameterFoundryGlobalConfiguration.get().connectionProfiles=[profile,dnsProfile,tlsProfile]
def providers=[]
['raw-rest-values','raw-rest-fallback','raw-rest-dns','raw-rest-tls','raw-tls-denied','status-old','status-body'].eachWithIndex{name,index->
 def provider=new RestProviderDefinition(name,index in [3,4]?'synthetic-raw-tls':index==2?'synthetic-raw-dns':'synthetic-raw-peer','value')
 provider.responseMode='active-choices-raw-scalar';provider.cacheTtlSeconds=0;provider.timeoutSeconds=10
 provider.rawIgnoredResponses=[new RestRawLiteral('null')];provider.rawTrailingChoices=[new RestRawLiteral('topic/stable')]
 if(index==0)provider.rawFailureChoices=[new RestRawFailureChoice('HTTP_STATUS','HTTP {status}: {uri}')]
 else if(index==1)provider.fallbackOptions=[new Option('topic/fallback')]
 else if(index==2)provider.rawFailureChoices=[new RestRawFailureChoice('DNS_UNRESOLVED','DNS unavailable')]
 else if(index in [3,4]){def row=new RestRawFailureChoice('TLS_REJECTED','TLS unavailable');row.fingerprint=index==3?tlsFingerprint:'0'*64;provider.rawFailureChoices=[row]}
 else {provider.rawFailureChoices=[new RestRawFailureChoice('HTTP_STATUS','reviewed-http-error')]}
 providers.add(provider)
 def job=j.createProject(FreeStyleProject,name)
 def choice=new FoundryChoiceParameterDefinition('REF');choice.providerId=name;choice.renderer='select';choice.defaultMode='first';choice.standardApiInputMode='active-choices';choice.activeChoicesFilterMode='none'
 job.addProperty(new ParametersDefinitionProperty(choice));job.save()
}
j.createProject(FreeStyleProject,'plain-core-breadcrumb-control')
ParameterFoundryGlobalConfiguration.get().providers=providers
ParameterFoundryGlobalConfiguration.get().save();j.save()
new File(j.rootDir,'raw-rest-ready.json').text=JsonOutput.toJson([core:Jenkins.VERSION,version:j.pluginManager.getPlugin('parameter-foundry').version,sha256:hash(j.pluginManager.getPlugin('parameter-foundry').archive.bytes),peer:peer,tlsPeer:tlsProfile.baseUrl,validTlsPeer:"https://owned-tls-peer.invalid:18183/api/",tlsFingerprint:tlsFingerprint])
