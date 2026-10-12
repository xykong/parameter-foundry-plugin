"""Run only after active-choices-multiple-init.groovy on a loopback, zero-executor Lab."""
import json
from pathlib import Path
from urllib.parse import urlsplit
from playwright.sync_api import sync_playwright
BASE='http://127.0.0.1:8080'
rows=[]
with sync_playwright()as pw:
 browser=pw.chromium.launch()
 for width,height in [(1440,1000),(390,844)]:
  context=browser.new_context(viewport={'width':width,'height':height});blocked=[]
  def guard(route):
   request=route.request
   if request.method not in ['GET','HEAD']and not urlsplit(request.url).path.endswith('/parameter-foundry/api/v1/resolve'):
    blocked.append(request.method);route.abort()
   else:route.continue_()
  context.route('**/*',guard);pair=[]
  for role in ['source','target']:
   page=context.new_page();page.goto(BASE+'/job/active-multiple-'+role+'/build?delay=0sec',wait_until='networkidle')
   control=page.locator('select[name=value]')if role=='source'else page.locator('[data-up-enhanced] select')
   model=control.evaluate('s=>({values:Array.from(s.options).map(o=>o.value),labels:Array.from(s.options).map(o=>o.text),selected:Array.from(s.selectedOptions).map(o=>o.value),size:s.size,multiple:s.multiple})')
   assert model['values']==['fallback','other']and model['selected']==[]and model['multiple']and model['size']==2,(role,model)
   control.select_option(['other']);assert control.evaluate('s=>Array.from(s.selectedOptions).map(o=>o.value)')==['other']
   control.evaluate("element=>element.scrollIntoView({block:'center',inline:'nearest'})")
   page.wait_for_timeout(100)
   visibility=control.evaluate("element=>{const r=element.getBoundingClientRect();const hit=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return {top:r.top,bottom:r.bottom,height:r.height,visible:hit===element||element.contains(hit)}}")
   assert visibility['visible'],(role,width,visibility)
   control.screenshot(path='/work/multiple-'+role+'-'+str(width)+'.png');pair.append(model);page.close()
  assert pair[0]==pair[1]and not blocked
  rows.append({'viewport':width,'sourceTargetEqual':True,'control':pair[0]});context.close()
 cases=[{}, {'value':''},{'value':[]},{'value':['other','fallback']},{'value':['a,b','quote"']},{'value':None},{'value':True},{'value':7},{'value':{'nested':'object'}}]
 pairs=[]
 request=browser.new_context().request
 for index,item in enumerate(cases):
  values=[]
  for role in ['source','target']:
   response=request.post(BASE+'/job/active-multiple-'+role+'/build',form={'json':json.dumps({'parameter':[{'name':'REGIONS',**item}]})},max_redirects=0)
   assert response.status in [200,201,302],response.status
   queuedItems=request.get(BASE+'/queue/api/json?tree=items[id,task[name],actions[parameters[name,value]]]').json()['items']
   matching=[entry for entry in queuedItems if entry['task']['name']=='active-multiple-'+role]
   assert len(matching)==1,(role,response.status,queuedItems)
   queued=matching[0]
   parameters=[p for a in queued.get('actions',[])for p in a.get('parameters',[])if p['name']=='REGIONS'];assert len(parameters)==1
   values.append(parameters[0]['value'])
   cancel=request.post(BASE+'/queue/cancelItem',params={'id':queued['id']});assert cancel.status in [200,204,302,303]
  assert values[0]==values[1];pairs.append({'index':index,'value':values[0]})
 request.dispose();browser.close()
result={'viewports':rows,'httpPairs':pairs,'neutralQueueItemsCancelled':len(pairs)*2,'businessBuildsScheduled':0,'browserFormsSubmitted':0,'limits':'Literal unfiltered multiple controls only; filtered/cascade/HTML sources require separate gates.'}
Path('/work/active-multiple-result.json').write_text(json.dumps(result,indent=2));print(json.dumps({'viewports':len(rows),'httpPairs':len(pairs),'neutralQueueItemsCancelled':len(pairs)*2,'businessBuildsScheduled':0}))
