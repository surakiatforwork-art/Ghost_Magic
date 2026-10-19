"""Node-handle acceptance through the real UI on the disposable project AVD."""
from pathlib import Path
import json,re
src=Path(__file__).with_name('classic_ui_smoke.py').read_text(encoding='utf-8-sig')
exec(src.split("assert 'GhostMagic_Validation'")[0])
assert 'GhostMagic_Validation' in adb('emu','avd','name')
adb('install','-r',str(base/'app/build/outputs/apk/debug/app-debug.apk'))
adb('shell','am','force-stop','com.ghostmagic.android');adb('shell','am','start','-W','-n','com.ghostmagic.android/.MainActivity')
beforeIds=set(adb('shell','run-as','com.ghostmagic.android','ls','files/flows').split())
click('New flow');adb('shell','input','text','HandlesProbe6');click('Create')
def point(x,y):
 r=tree();n=next(n for n in r.iter('node') if n.get('content-desc','').startswith('Flow graph.'))
 left,top,_,_=map(int,re.findall(r'\d+',n.get('bounds')))
 metrics=adb('shell','wm','density');density=int(re.findall(r'\d+',metrics)[-1])/160
 return str(round(left+x*density)),str(round(top+y*density))
def tap(x,y):adb('shell','input','tap',*point(x,y));tree()
def drag(x,y,a,b):
 start=point(x,y);end=point(a,b);adb('shell','input','swipe',*start,*end,'700');tree()
def saved():
 for name in adb('shell','run-as','com.ghostmagic.android','ls','files/flows').split():
  if not name.endswith('.json'):continue
  d=json.loads(adb('exec-out','run-as','com.ghostmagic.android','cat','files/flows/'+name))
  if name not in beforeIds:return d
 raise AssertionError('Fixture not saved')
tap(162,104);screenshot('node-controls.png');tap(24,104)
assert any(n.get('text')=='Trigger type' for n in tree().iter('node'));click('Cancel')
drag(162,188,250,550);click('Action');click('Log message');click('Apply');click('Save')
d=saved();assert len(d['nodes'])==2 and len(d['edges'])==1
first=next(n for n in d['nodes'] if n['type']=='action.log');assert abs(first['x']-130)<4 and abs(first['y']-550)<4
# Add another node from this exact origin rather than appending after the last node.
tap(162,104);drag(162,188,162,350);click('Replace');click('Condition');click('Battery above');click('Apply');click('Save')
d=saved();condition=next(n for n in d['nodes'] if n['type']=='condition.battery.above')
assert next(e for e in d['edges'] if e['from']==d['startId'])['to']==condition['id']
tap(162,404);drag(162,488,250,604);click('false');click('Save')
d=saved();assert any(e['from']==condition['id'] and e['port']=='false' and e['to']==first['id'] for e in d['edges'])
screenshot('node-connected.png')
(base/'evidence/node-controls-smoke.json').write_text(json.dumps({'selectedNodeControls':True,'leftEditOpensSettings':True,'blankDropCreatesAtDropPosition':True,'sourceConnectionReplaced':True,'conditionFalseDragConnectsExistingNode':True,'savedRoutesVerified':True},indent=2))
print('Node control UI smoke passed')
