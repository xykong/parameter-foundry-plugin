import json
from pathlib import Path
from urllib.parse import urlsplit
from playwright.sync_api import sync_playwright
BASE='http://127.0.0.1:8080'
results=[]
with sync_playwright()as pw:
 browser=pw.chromium.launch()
 for width,height in [(1440,1000),(390,844)]:
  context=browser.new_context(viewport={'width':width,'height':height});blocked=[]
  def guard(route):
   request=route.request;leaf=urlsplit(request.url).path.rsplit('/',1)[-1]
   if request.method not in ['GET','HEAD']and leaf not in ['resolve','getChoicesForUI','getChoicesAsStringForUI','doUpdate']:
    blocked.append({'method':request.method,'path':leaf});route.abort()
   else:route.continue_()
  context.route('**/*',guard);pair=[]
  for role in ['source','target']:
   page=context.new_page();page.goto(BASE+'/job/active-filter-'+role+'/build?delay=0sec',wait_until='networkidle')
   control=page.locator('select[name=value]')if role=='source'else page.locator('[data-up-enhanced] select')
   query=page.locator('input[name=test]')if role=='source'else page.locator('[data-up-enhanced] input[type=search]')
   def state():return control.evaluate('s=>({values:Array.from(s.options).map(o=>o.value),labels:Array.from(s.options).map(o=>o.text),selected:Array.from(s.selectedOptions).map(o=>o.value),size:s.size})')
   samples=[{'case':'initial','state':state()}];control.select_option(['other']);samples.append({'case':'manual','state':state()})
   for value in ['fall','fallb','^alph','xxxxx','','[xxxx','a&amp']:
    query.fill(value)
    query.dispatch_event('keyup')
    page.wait_for_timeout(150);samples.append({'case':value,'state':state()})
   query.fill('^alph');query.dispatch_event('keyup');control.select_option(['ALPHA']);query.press('ArrowLeft');page.wait_for_timeout(100);samples.append({'case':'unchanged-keyup','state':state()})
   query.press('Escape');page.wait_for_timeout(100);samples.append({'case':'escape','query':query.input_value(),'state':state()})
   control.evaluate("element=>element.scrollIntoView({block:'center'})")
   control.screenshot(path='/work/filter-'+role+'-'+str(width)+'.png');pair.append(samples);page.close()
  assert pair[0]==pair[1],(width,pair)
  cascade=[]
  for role in ['source','core-source','retain-valid','default','drop-invalid']:
   page=context.new_page();page.goto(BASE+'/job/active-retain-'+role+'/build?delay=0sec',wait_until='networkidle')
   if role in ['source','core-source']:
    container=page.locator('div[name=parameter]').filter(has=page.locator('input[name=name][value=SIZE]'));child=container.locator('select[name=value]')
    parent=page.locator('[data-parameter-name=MODE] [data-up-enhanced] select')if role=='source'else page.locator('div[name=parameter]').filter(has=page.locator('input[name=name][value=MODE]')).locator('select[name=value]')
   else:
    page.wait_for_function("document.querySelector('[data-parameter-name=SIZE]').dataset.upState==='ready'")
    child=page.locator('[data-parameter-name=SIZE] [data-up-enhanced] select');parent=page.locator('[data-parameter-name=MODE] [data-up-enhanced] select')
    assert page.locator('[data-parameter-name=SIZE] [data-up-enhanced] input[type=search]').count()==0
   assert child.input_value()=='small';child.select_option('large');parent.select_option('production')
   if role in ['source','core-source']:page.wait_for_timeout(700)
   else:page.wait_for_function("document.querySelector('[data-parameter-name=SIZE]').dataset.upState==='ready'")
   value=child.input_value();assert value==('small'if role in ['default','core-source','drop-invalid']else'large'),(role,value)
   child.evaluate("element=>element.scrollIntoView({block:'center'})");child.screenshot(path='/work/cascade-'+role+'-'+str(width)+'.png')
   cascade.append({'role':role,'selectedAfterParentChange':value});page.close()
  large=[]
  for role in ['source','target']:
   page=context.new_page();page.goto(BASE+'/job/active-large-'+role+'/build?delay=0sec',wait_until='networkidle')
   control=page.locator('select[name=value]')if role=='source'else page.locator('[data-up-enhanced] select')
   assert control.locator('option').count()==181
   control.select_option(['region-180']);assert control.evaluate('s=>Array.from(s.selectedOptions).map(o=>o.value)')==['region-180']
   if role=='target':assert page.locator('[data-up-enhanced] input[type=search]').count()==0
   large.append({'role':role,'options':181,'tailSelectable':True});page.close()
  assert not blocked;results.append({'viewport':width,'filterPairs':pair[0],'cascade':cascade,'large':large});context.close()
 browser.close()
Path('/work/active-ui-policy-result.json').write_text(json.dumps(results,indent=2));print(json.dumps({'viewports':len(results),'filterCases':len(results[0]['filterPairs']),'cascadeRoles':5,'formsSubmitted':0,'businessBuildsScheduled':0}))
