"""Only for the literal-reference seed in an owned zero-executor loopback Lab."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

BASE = 'http://127.0.0.1:8080'
rows = []
cancelled = 0
with sync_playwright() as pw:
    browser = pw.chromium.launch()
    for width, height in [(1440, 1000), (390, 844)]:
        context = browser.new_context(viewport={'width': width, 'height': height})
        pair = []
        for role in ['source', 'target']:
            page = context.new_page()
            page.goto(BASE + '/job/active-reference-' + role + '/build?delay=0sec', wait_until='networkidle')
            field = page.locator('div[name=parameter]').filter(has=page.locator('input[name=name][value=NOTICE]'))
            bold = field.locator('b')
            assert bold.inner_text() == 'presentation only'
            value = field.locator('input[name=value]')
            assert value.count() == 1 and value.input_value() == '' and not value.is_visible()
            parent = page.locator('div[name=parameter]').filter(has=page.locator('input[name=name][value=MODE]')).locator('select[name=value]')
            parent.select_option('production')
            page.wait_for_timeout(600)
            assert bold.inner_text() == 'presentation only' and value.input_value() == ''
            bold.scroll_into_view_if_needed()
            for _ in range(4):
                rect = bold.bounding_box()
                if rect['y'] + rect['height'] > height - 140:
                    page.mouse.wheel(0, 200)
                elif rect['y'] < 100:
                    page.mouse.wheel(0, -120)
                else:
                    break
                page.wait_for_timeout(100)
            page.screenshot(path='/work/reference-page-' + role + '-' + str(width) + '.png')
            assert bold.evaluate('e=>{const r=e.getBoundingClientRect();const hit=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return hit===e||e.contains(hit)}')
            field.screenshot(path='/work/reference-' + role + '-' + str(width) + '.png')
            pair.append({'presentation': bold.inner_text(), 'value': value.input_value(), 'type': value.get_attribute('type'), 'hidden': not value.is_visible()})
            page.get_by_role('button', name='Build', exact=True).click()
            page.wait_for_load_state('networkidle')
            queue = context.request.get(BASE + '/queue/api/json?tree=items[id,task[name],actions[parameters[name,value]]]').json()['items']
            entries = [e for e in queue if e['task']['name'] == 'active-reference-' + role]
            assert len(entries) == 1
            entry = entries[0]
            parameters = [p for a in entry.get('actions', []) for p in a.get('parameters', []) if p['name'] == 'NOTICE']
            assert len(parameters) == 1 and parameters[0]['value'] == ''
            assert context.request.post(BASE + '/queue/cancelItem', params={'id': entry['id']}).status in [200, 204, 302, 303]
            cancelled += 1
            page.close()
        assert pair[0] == pair[1]
        page = context.new_page()
        page.goto(BASE + '/job/active-reference-sanitizer/build?delay=0sec', wait_until='networkidle')
        content = page.locator('[data-foundry-reference-content]')
        assert content.inner_html() == '<b>safe</b>'
        rows.append({'viewport': width, 'sourceTargetEqual': True, 'control': pair[0], 'sanitizer': True})
        context.close()
    request = browser.new_context().request
    cases = [{}, {'value': ''}, {'value': ' outside '}, {'value': []}, {'value': ['a,b', 'quote"']}, {'value': None}, {'value': True}, {'value': 7}, {'value': {'nested': 'object'}}]
    pairs = []
    for index, item in enumerate(cases):
        values = []
        for role in ['source', 'target']:
            response = request.post(BASE + '/job/active-reference-' + role + '/build', form={'json': json.dumps({'parameter': [{'name': 'NOTICE', **item}]})}, max_redirects=0)
            assert response.status in [200, 201, 302]
            queue = request.get(BASE + '/queue/api/json?tree=items[id,task[name],actions[parameters[name,value]]]').json()['items']
            entries = [e for e in queue if e['task']['name'] == 'active-reference-' + role]
            assert len(entries) == 1
            entry = entries[0]
            parameters = [p for a in entry.get('actions', []) for p in a.get('parameters', []) if p['name'] == 'NOTICE']
            assert len(parameters) == 1
            values.append(parameters[0]['value'])
            assert request.post(BASE + '/queue/cancelItem', params={'id': entry['id']}).status in [200, 204, 302, 303]
            cancelled += 1
        assert values[0] == values[1]
        pairs.append({'index': index, 'value': values[0]})
    schema = request.get(BASE + '/job/active-reference-target/parameter-foundry/api/v1/schema').json()
    reference = next(p for p in schema['parameters'] if p['name'] == 'NOTICE')
    assert reference['supported'] and reference['type'] == 'string' and reference['owner'] == 'parameter-foundry'
    typed = []
    for values, expected in [({}, ''), ({'NOTICE': ''}, ''), ({'NOTICE': ' outside '}, ' outside ')]:
        response = request.post(BASE + '/job/active-reference-target/parameter-foundry/api/v1/build', data=json.dumps({'values': values}), headers={'Content-Type': 'application/json'}, max_redirects=0)
        assert response.status in [201, 303]
        queue = request.get(BASE + '/queue/api/json?tree=items[id,actions[parameters[name,value]]]').json()['items']
        assert len(queue) == 1
        entry = queue[0]
        parameters = [p for a in entry.get('actions', []) for p in a.get('parameters', []) if p['name'] == 'NOTICE']
        assert len(parameters) == 1 and parameters[0]['value'] == expected
        assert request.post(BASE + '/queue/cancelItem', params={'id': entry['id']}).status in [200, 204, 302, 303]
        cancelled += 1
        typed.append({'values': values, 'value': expected})
    for invalid in [None, 7, [], {}]:
        response = request.post(BASE + '/job/active-reference-target/parameter-foundry/api/v1/build', data=json.dumps({'values': {'NOTICE': invalid}}), headers={'Content-Type': 'application/json'}, max_redirects=0)
        assert response.status == 400
    assert request.get(BASE + '/queue/api/json?tree=items[id]').json()['items'] == []
    request.dispose()
    browser.close()
result = {'viewports': rows, 'httpPairs': pairs, 'typedHttp': typed, 'typedRejectedTypes': 4, 'browserFormsSubmitted': 4, 'neutralQueueItemsCancelled': cancelled, 'businessBuildsScheduled': 0, 'finalQueueEmpty': True, 'scope': 'Constant literal formatted HTML with an empty hidden field; dynamic scripts and embedded input forms are not covered.'}
Path('/work/active-reference-result.json').write_text(json.dumps(result, indent=2))
print(json.dumps({'viewports': len(rows), 'httpPairs': len(pairs), 'neutralQueueItemsCancelled': cancelled, 'businessBuildsScheduled': 0}))
