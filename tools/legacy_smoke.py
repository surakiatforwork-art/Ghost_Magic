"""Synthetic-fixture SAF import/export round trip on the isolated project AVD."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

base=Path(__file__).resolve().parents[1]
serial='emulator-5582';package='com.ghostmagic.android'
fixture=base/'app/src/androidTest/assets/synthetic-legacy.flows'

def call(*args,check=True):return subprocess.run(['adb','-s',serial,*args],capture_output=True,check=check)
def adb(*args):return call(*args).stdout.decode('utf-8',errors='replace')
def tree():
    adb('shell','uiautomator','dump','/sdcard/ghost-legacy-window.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/ghost-legacy-window.xml'))
def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds',''))))
def nodes(root,label):
    return [n for n in root.iter('node') if label.casefold() in (n.get('text','').casefold(),n.get('content-desc','').casefold()) and len(bounds(n))==4 and bounds(n)[2]>bounds(n)[0] and bounds(n)[3]>bounds(n)[1]]
def tap(n):
    x1,y1,x2,y2=bounds(n);adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def click(label):
    found=nodes(tree(),label);assert found,'Visible control missing: '+label;tap(found[-1])
def contains(text):return text in ET.tostring(tree(),encoding='unicode')
def await_text(text):
    deadline=time.monotonic()+25
    while time.monotonic()<deadline:
        if contains(text):return
        time.sleep(.1)
    raise AssertionError('UI did not show '+text)
def downloads():
    click('Show roots');click('Downloads')
def image(name):
    remote='/sdcard/ghost-legacy-'+name+'.png';adb('shell','screencap','-p',remote);adb('pull',remote,str(base/'evidence'/('legacy-'+name+'.png')))
def export(label,name):
    click(label);root=tree()
    fields=[n for n in root.iter('node') if n.get('class')=='android.widget.EditText'];assert len(fields)==1,'Filename field missing'
    field=fields[0];old=field.get('text','');tap(field);adb('shell','input','keyevent','123')
    adb('shell','input','keyevent',*(['67']*(len(old)+2)));adb('shell','input','text',name);click('Save')
    await_text('Legacy review');deadline=time.monotonic()+10
    destination=base/'evidence'/name
    while time.monotonic()<deadline:
        result=call('pull','/sdcard/Download/'+name,str(destination),check=False)
        if result.returncode==0:return destination
        time.sleep(.1)
    raise AssertionError('Export file missing')

assert adb('shell','getprop','ro.kernel.qemu').strip()=='1'
assert 'GhostMagic_Validation' in adb('emu','avd','name'),'Refuses other AVDs'
(base/'evidence/legacy-smoke.json').unlink(missing_ok=True)
adb('install','-r',str(base/'app/build/outputs/apk/debug/app-debug.apk'))
adb('push',str(fixture),'/sdcard/Download/ghost-legacy-synthetic.flows')
adb('shell','am','start','-W','-f','0x10008000','-n',package+'/.MainActivity')
before=adb('shell','run-as',package,'ls','files/flows').splitlines()
click('Import');downloads();click('ghost-legacy-synthetic.flows');await_text('Legacy review')
assert contains('REVIEW ONLY');assert contains('1 flows');assert contains('4 shared objects');image('review')
click('Migration report');await_text('Legacy migration report')
digest=hashlib.sha256(fixture.read_bytes()).hexdigest()
screen=ET.tostring(tree(),encoding='unicode');assert digest in screen and 'plugin.unknown' in screen and 'FALSE' in screen and 'NOT EXECUTABLE' in screen
image('report');click('Close')
token=uuid.uuid4().hex[:6]
original=export('Export original .flows','ghost-legacy-roundtrip-'+token+'.flows')
assert original.read_bytes()==fixture.read_bytes(),'Source bytes changed on export'
report=export('Export report','ghost-legacy-report-'+token+'.txt')
text=report.read_text(encoding='utf-8');assert digest in text and 'NOT EXECUTABLE' in text and 'plugin.unknown' in text
adb('shell','am','force-stop',package);adb('shell','am','start','-W','-f','0x10008000','-n',package+'/.MainActivity')
click('Legacy');await_text('Legacy library');assert contains('ghost-legacy-synthetic.flows');image('library')
after=adb('shell','run-as',package,'ls','files/flows').splitlines();assert before==after,'Import changed runnable flows'
click('Close')
# Regression check for the refactored asynchronous native-JSON import path.
fid=str(uuid.uuid4());start=str(uuid.uuid4());end=str(uuid.uuid4())
native=dict(format='ghostmagic.flow',schemaVersion=1,id=fid,name='Native periodic import check',revision=1,startId=start,
    nodes=[dict(id=start,type='trigger.periodic',config={'intervalMinutes':'15'},x=42,y=50),dict(id=end,type='action.log',config={'message':'Do not auto-run'},x=42,y=220)],
    edges=[{'from':start,'port':'next','to':end}])
native_file=base/'.runtime'/('ghost-native-import-'+token+'.ghostflow.json');native_file.write_text(json.dumps(native),encoding='utf-8')
adb('push',str(native_file),'/sdcard/Download/'+native_file.name)
click('Import');downloads();click(native_file.name);await_text('Native periodic import check (imported)')
assert contains('FLOW STUDIO / PERIODIC')
native_after=adb('shell','run-as',package,'ls','files/flows').splitlines();created=set(native_after)-set(after);assert len(created)==1
copy=json.loads(adb('exec-out','run-as',package,'cat','files/flows/'+created.pop()));assert copy['id']!=fid and copy['name'].endswith('(imported)')
jobs=adb('shell','dumpsys','jobscheduler')
registered=[block for block in re.split(r'(?=JOB #)',jobs) if block.startswith('JOB #') and 'com.ghostmagic.android/.FlowJobService' in block]
assert not registered,'Read-only import unexpectedly activated jobs'
record=dict(fixture='synthetic; not an actual Automagic export',sourceSha256=digest,
    safImport=True,reportShowsSourceIdentityAndUnknownPlugin=True,exportOriginalByteExact=True,
    safReportExport=True,librarySurvivesProcessRestart=True,nativeFlowIdsUnchanged=True,activeJobs=0,
    nativePeriodicJsonImportCreatesInactiveCopy=True,
    exportedOriginal=original.name,exportedReport=report.name)
(base/'evidence/legacy-smoke.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
print(json.dumps(record,indent=2))
