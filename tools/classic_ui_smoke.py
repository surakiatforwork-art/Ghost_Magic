"""Classic UI acceptance smoke; own isolated emulator only."""
from pathlib import Path
import subprocess,xml.etree.ElementTree as ET,re,json
base=Path(__file__).resolve().parents[1]
def adb(*a):return subprocess.run(['adb','-s','emulator-5582',*a],check=True,capture_output=True).stdout.decode('utf8',errors='replace')
def tree():
 adb('shell','uiautomator','dump','/sdcard/classic-ui.xml');return ET.fromstring(adb('shell','cat','/sdcard/classic-ui.xml'))
def click(text):
 for n in tree().iter('node'):
  if n.get('text','').casefold()==text.casefold() or n.get('content-desc','').casefold()==text.casefold():
   x,y,r,b=map(int,re.findall(r'\d+',n.get('bounds')))
   if r>x and b>y:adb('shell','input','tap',str((x+r)//2),str((y+b)//2));return
 raise AssertionError('Missing '+text)
def screenshot(name):
 tree()
 adb('shell','screencap','-p','/sdcard/classic.png');adb('pull','/sdcard/classic.png',str(base/'evidence'/name))
assert 'GhostMagic_Validation' in adb('emu','avd','name')
adb('install','-r',str(base/'app/build/outputs/apk/debug/app-debug.apk'))
adb('shell','am','force-stop','com.ghostmagic.android');adb('shell','am','start','-W','-n','com.ghostmagic.android/.MainActivity')
screenshot('classic-flows.png');click('Open flow');screenshot('classic-graph.png');click('Add node')
r=tree();labels={n.get('text') for n in r.iter('node')};assert {'Trigger','Condition','Action'}<=labels
click('Trigger');r=tree();assert any(n.get('text')=='Periodic trigger' for n in r.iter('node'))
adb('shell','input','keyevent','4');click('Add node');click('Action');click('OCR Text Reader')
r=tree();assert any(n.get('text')=='Action type' for n in r.iter('node'));screenshot('classic-action.png')
adb('shell','input','keyevent','4');click('Run');r=tree();assert any('Execution trace' in n.get('text','') for n in r.iter('node'))
(base/'evidence/classic-ui-smoke.json').write_text(json.dumps({'flowList':True,'graph':True,'separateCategories':True,'triggerPicker':True,'fullScreenOcrSettings':True,'executionTraceOpens':True},indent=2))
print('Classic UI smoke passed')
