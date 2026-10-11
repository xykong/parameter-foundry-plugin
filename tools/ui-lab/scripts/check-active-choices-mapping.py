#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["playwright>=1.55,<2"]
# ///
"""Read-only comparisons for the isolated active-choices-mapping UI Lab profile.

Start the profile on localhost:8080 with zero executors before running this script.
It never submits a build, modifies Job configuration, or calls external services.
"""
from pathlib import Path
import asyncio,json
from playwright.async_api import async_playwright
R=Path(__file__).resolve().parents[1]/'.cache/active-choices-mapping';R.mkdir(parents=True,exist_ok=True);BASE='http://127.0.0.1:8080'
async def main():
 async with async_playwright() as pw:
  b=await pw.chromium.launch();c=await b.new_context(viewport={'width':1440,'height':1000});blocked=[];requests=[];overrides=[];failNext=False
  async def guard(route):
   nonlocal failNext
   q=route.request
   if failNext and '/job/active-map-target/parameter-foundry/api/v1/resolve' in q.url and q.method=='POST':
    failNext=False;await route.fulfill(status=503,content_type='application/json',body='{"ok":false,"errors":[{"message":"Synthetic transport failure"}]}');return
   if q.method not in ('GET','HEAD')and not('/parameter-foundry/api/v1/resolve'in q.url or '/$stapler/bound/'in q.url):blocked.append(q.url);await route.abort();return
   await route.continue_()
  await c.route('**/*',guard);pages={};rows=[]
  for role in ('source','target'):
   p=await c.new_page();await p.goto(BASE+'/job/'+('active-map-source' if role=='source' else 'active-map-target')+'/build?delay=0sec',wait_until='networkidle');pages[role]=p;p.on('response',lambda r: requests.append({'status':r.status,'path':r.url.split('/')[-1],'body':r.request.post_data}) if '/$stapler/bound/' in r.url else None);await p.screenshot(path=str(R/(role+'-initial.png')),full_page=True);(R/(role+'-initial-private.json')).write_text(json.dumps({'title':await p.title(),'body':await p.locator('body').inner_text(),'parentFields':await p.locator('[data-parameter-name="MODE"]').count(),'enhancedRadios':await p.locator('[data-parameter-name="MODE"] input[type="radio"]').count()},indent=2))
  for mode in ['development','production','development','production','development']:
   if len(rows)>=3:
    for role,p in pages.items():
     previous=p.locator('select[name="value"]').nth(1)if role=='source'else p.locator('[data-parameter-name="CHOICE"] [data-up-enhanced] select');values=await previous.locator('option').evaluate_all('(xs)=>xs.map(x=>x.value)');await previous.select_option(values[1])
   expected=['true','false']if mode=='production'else['false','true'];captures={}
   for role,p in pages.items():
    if role=='source':await p.locator('select[name="value"]').nth(0).select_option(mode)
    else:await p.locator('[data-parameter-name="MODE"] input[type="radio"][value="'+mode+'"]').check()
    await p.wait_for_timeout(300);await p.wait_for_load_state('networkidle')
    select=p.locator('select[name="value"]').nth(1)if role=='source'else p.locator('[data-parameter-name="CHOICE"] [data-up-enhanced] select')
    try:
     await p.wait_for_function('(x)=>{const s=x.index==null?document.querySelector(x.selector):document.querySelectorAll(x.selector)[x.index];return s&&JSON.stringify(Array.from(s.options).map(o=>o.value))===JSON.stringify(x.values);}',arg={'selector':'select[name="value"]'if role=='source'else'[data-parameter-name="CHOICE"] [data-up-enhanced] select','values':expected,'index':1 if role=='source' else None})
    except Exception:
     await p.screenshot(path=str(R/(role+'-failure-'+mode+'.png')),full_page=True)
     (R/'failure-state-private.json').write_text(json.dumps({'role':role,'mode':mode,'body':await p.locator('body').inner_text(),'actualOptions':await select.locator('option').evaluate_all('(xs)=>xs.map(x=>x.value)'),'requests':requests,'overrides':overrides},indent=2));raise
    values=await select.locator('option').evaluate_all('(xs)=>xs.map(x=>x.value)');selected=await select.input_value()
    if role=='target':
     child=p.locator('[data-parameter-name="CHOICE"]');assert await child.locator('input[type="search"]').count()==0;assert await child.get_attribute('data-up-state')=='ready'
     deps=json.loads(await child.locator('[data-up-dependencies-json]').input_value());assert deps=={'MODE':mode}
    else:
     field=select.locator('xpath=ancestor::*[contains(@class,"jenkins-form-item")][1]');assert await field.locator('input[type="search"],input[type="text"]').count()==0
    await p.screenshot(path=str(R/(role+'-'+mode+'.png')),full_page=True);captures[role]={'options':values,'selected':selected,'filterVisible':False}
   (R/'browser-partial-result.json').write_text(json.dumps({'mode':mode,'captures':captures},indent=2));assert captures['source']==captures['target'];rows.append({'mode':mode,'source':captures['source'],'target':captures['target']})
  mobile=[]
  for p in pages.values():await p.set_viewport_size({'width':390,'height':844})
  for mode in ['development','production','development']:
   expected=['true','false']if mode=='production'else['false','true'];captures={}
   for role,p in pages.items():
    if role=='source':await p.locator('select[name="value"]').nth(0).select_option(mode)
    else:await p.locator('[data-parameter-name="MODE"] input[type="radio"][value="'+mode+'"]').check()
    await p.wait_for_timeout(300);await p.wait_for_load_state('networkidle')
    select=p.locator('select[name="value"]').nth(1)if role=='source'else p.locator('[data-parameter-name="CHOICE"] [data-up-enhanced] select')
    await p.wait_for_function('(x)=>{const s=x.index==null?document.querySelector(x.selector):document.querySelectorAll(x.selector)[x.index];return s&&JSON.stringify(Array.from(s.options).map(o=>o.value))===JSON.stringify(x.values);}',arg={'selector':'select[name="value"]'if role=='source'else'[data-parameter-name="CHOICE"] [data-up-enhanced] select','values':expected,'index':1 if role=='source'else None})
    assert await select.locator('option').evaluate_all('(xs)=>xs.map(x=>x.value)')==expected
    assert await select.input_value()==expected[0];captures[role]={'values':expected,'selected':expected[0]};await p.screenshot(path=str(R/(role+'-mobile-'+mode+'.png')),full_page=True)
   assert captures['source']==captures['target'];mobile.append({'mode':mode,'matched':True})
  p=pages['target'];child=p.locator('[data-parameter-name="CHOICE"]')
  for mode in ['production','development','production']:await p.locator('[data-parameter-name="MODE"] input[type="radio"][value="'+mode+'"]').check()
  await p.wait_for_function("() => {const f=document.querySelector('[data-parameter-name=\"CHOICE\"]');return f.dataset.upState==='ready'&&JSON.parse(f.querySelector('[data-up-dependencies-json]').value).MODE==='production'&&f.querySelector('[data-up-enhanced] select').value==='true';}")
  failNext=True;await p.locator('[data-parameter-name="MODE"] input[type="radio"][value="development"]').check()
  await p.wait_for_function("() => document.querySelector('[data-parameter-name=\"CHOICE\"]').dataset.upState==='stale'")
  for mode in ['production','development']:
   await p.locator('[data-parameter-name="MODE"] input[type="radio"][value="'+mode+'"]').check();await p.wait_for_load_state('networkidle');await p.wait_for_function("(mode)=>{const f=document.querySelector('[data-parameter-name=\"CHOICE\"]');return f.dataset.upState==='ready'&&JSON.parse(f.querySelector('[data-up-dependencies-json]').value).MODE===mode&&f.querySelector('[data-up-enhanced] select').value===(mode==='production'?'true':'false');}",arg=mode)
  assert await child.locator('[data-up-enhanced] select').input_value()=='false';assert await child.get_attribute('data-up-state')=='ready'
  p=pages['target'];crumb=await p.evaluate('()=>({name:document.head.dataset.crumbHeader,value:document.head.dataset.crumbValue})');api=[]
  for mode in ('development','production','unknown',None):
   body={'parameter':'CHOICE','dependencies':{'MODE':mode},'version':1};res=await c.request.post(BASE+'/job/active-map-target/parameter-foundry/api/v1/resolve',data=json.dumps(body),headers={'Content-Type':'application/json',crumb['name']:crumb['value']});d=await res.json();assert res.status==200 and d['ok'];assert d.get('filterMode','')=='';expected=['true','false']if mode=='production'else['false','true'];assert [x['value']for x in d['options']]==expected;assert d['default']==expected[0];api.append({'mode':mode,'values':expected,'uiDefault':d['default'],'filterModeAbsent':True})
  absent=await c.request.post(BASE+'/job/active-map-target/parameter-foundry/api/v1/resolve',data=json.dumps({'parameter':'CHOICE'}),headers={'Content-Type':'application/json',crumb['name']:crumb['value']});assert absent.status==400
  extras=[]
  for role in ['ordinary','none']:
   p=await c.new_page();await p.goto(BASE+'/job/active-map-'+role+'/build?delay=0sec',wait_until='networkidle');child=p.locator('[data-parameter-name="CHOICE"]');await p.locator('[data-parameter-name="MODE"] input[type="radio"][value="development"]').check();await p.wait_for_function("() => document.querySelector('[data-parameter-name=\"CHOICE\"]').dataset.upState==='ready'")
   if role=='ordinary':await child.locator('[data-up-enhanced] select').select_option('false')
   await p.locator('[data-parameter-name="MODE"] input[type="radio"][value="production"]').check();await p.wait_for_function("() => {const f=document.querySelector('[data-parameter-name=\"CHOICE\"]');return f.dataset.upState==='ready'&&JSON.parse(f.querySelector('[data-up-dependencies-json]').value).MODE==='production';}")
   if role=='ordinary':assert await child.locator('[data-up-enhanced] select').input_value()=='false'
   else:assert await child.locator('[data-up-enhanced] input[type="radio"]:checked').count()==0
   extras.append({'role':role,'selectionContractPreserved':True})
  assert not blocked;v={'browser':b.version,'pairs':rows,'resolvedApi':api,'mobile':mobile,'rapidChangesFinalStateVerified':True,'transportFailureRecoveryVerified':True,'missingResolveDependencyRejected':True,'ordinaryAndNone':extras,'candidateUiOverride':False,'extendedTargetOnly':False,'sourceDesktopPairEvidence':'setup-attempt14-browser-result.json','formsSubmitted':0,'businessBuildsScheduled':0};(R/'browser-result.json').write_text(json.dumps(v,indent=2));print(json.dumps(v));await b.close()
asyncio.run(main())
