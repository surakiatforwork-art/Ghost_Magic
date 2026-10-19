"""Real JobScheduler, stop, process-death and reboot checks on the isolated project AVD.

Shell-forced delivery verifies the runtime, not natural 15-minute timing or Doze latency.
"""
from pathlib import Path
import json
import re
import subprocess
import sqlite3
from contextlib import closing
import time
import uuid
import xml.etree.ElementTree as ET

base=Path(__file__).resolve().parents[1]
serial='emulator-5582'
package='com.ghostmagic.android'
results=[]

def raw(*args,check=True):
    return subprocess.run(['adb','-s',serial,*args],capture_output=True,check=check)

def adb(*args): return raw(*args).stdout.decode('utf-8',errors='replace')

def tree():
    adb('shell','uiautomator','dump','/sdcard/ghost-background-window.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/ghost-background-window.xml'))

def bounds(node):return list(map(int,re.findall(r'\d+',node.get('bounds',''))))

def tap(node):
    x1,y1,x2,y2=bounds(node)
    assert x2>x1 and y2>y1
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))

def find(root,label):
    for node in root.iter('node'):
        b=bounds(node)
        if node.get('text','').casefold()==label.casefold() and len(b)==4 and b[2]>b[0] and b[3]>b[1]:return node

def click(label,scroll=False):
    for attempt in range(6 if scroll else 1):
        root=tree();node=find(root,label)
        if node is not None:tap(node);return
        if scroll:
            bars=[n for n in root.iter('node') if n.get('class')=='android.widget.HorizontalScrollView']
            assert bars,'Toolbar not found'
            x1,y1,x2,y2=bounds(bars[0]);y=(y1+y2)//2
            adb('shell','input','swipe',str(int(x1+(x2-x1)*.8)),str(y),str(int(x1+(x2-x1)*.2)),str(y),'300')
    raise AssertionError(f'Button not found: {label}')

def launch():adb('shell','am','start','-W','-f','0x10008000','-n',package+'/.MainActivity')

def open_flow(name):
    launch()
    for attempt in range(8):
        root=tree()
        for node in root.iter('node'):
            if any(n.get('text')==name for n in list(node)):
                button=find(node,'Open flow')
                if button is not None:tap(button);return
        scrolls=[n for n in root.iter('node') if n.get('class')=='android.widget.ScrollView']
        if not scrolls:break
        x1,y1,x2,y2=bounds(scrolls[0]);x=(x1+x2)//2
        adb('shell','input','swipe',str(x),str(y2-120),str(x),str(y1+100),'350')
    raise AssertionError('Flow not visible: '+name)

def image(name):
    remote='/sdcard/ghost-background-'+name+'.png';adb('shell','screencap','-p',remote);adb('pull',remote,str(base/'evidence'/('background-'+name+'.png')))

def seed(name,delay):
    fid=str(uuid.uuid5(uuid.NAMESPACE_URL,'ghostmagic.background-smoke/'+name));start=str(uuid.uuid4());end=str(uuid.uuid4())
    nodes=[dict(id=start,type='trigger.periodic',config={'intervalMinutes':'15'},x=42,y=50)]
    edges=[]
    if delay:
        wait=str(uuid.uuid4());nodes.append(dict(id=wait,type='action.delay',config={'milliseconds':'60000'},x=42,y=210));edges.append(dict(from_=start,port='next',to=wait));previous=wait
    else:previous=start
    nodes.append(dict(id=end,type='action.log',config={'message':name+' finished'},x=42,y=380));edges.append(dict(from_=previous,port='next',to=end))
    edges=[{('from' if k=='from_' else k):v for k,v in e.items()} for e in edges]
    doc=dict(format='ghostmagic.flow',schemaVersion=1,id=fid,name=name,revision=1,startId=start,nodes=nodes,edges=edges)
    local=base/'evidence'/(fid+'.ghostflow.json');local.write_text(json.dumps(doc,indent=2),encoding='utf-8')
    remote='/data/local/tmp/ghost-'+fid+'.json';adb('push',str(local),remote)
    adb('shell','run-as',package,'mkdir','-p','files/flows');adb('shell','run-as',package,'cp',remote,'files/flows/'+fid+'.json');adb('shell','rm',remote)
    return fid

def enable(name,fid):
    open_flow(name);click('Schedule',scroll=True);image('schedule');click('Enable' if find(tree(),'Enable') is not None else 'Update')
    tree() # Wait for the schedule dialog and its database transaction to finish.
    # Android's dump may leave PersistableBundle parcelled. Read a coherent snapshot
    # of this isolated debug app's own DB instead of guessing a job ID from a hash.
    folder=base/'.runtime/probe';folder.mkdir(parents=True,exist_ok=True)
    for suffix in ('','-wal','-shm'):(folder/('runtime.db'+suffix)).unlink(missing_ok=True)
    pid=adb('shell','pidof',package).strip();assert re.fullmatch(r'\d+',pid)
    adb('shell','run-as',package,'kill','-STOP',pid)
    try:
        for suffix in ('','-wal'):
            result=raw('exec-out','run-as',package,'cat','databases/runtime.db'+suffix,check=False)
            if result.returncode==0:(folder/('runtime.db'+suffix)).write_bytes(result.stdout)
    finally:adb('shell','run-as',package,'kill','-CONT',pid)
    with closing(sqlite3.connect(folder/'runtime.db')) as db:row=db.execute('select job_id,enabled from schedules where flow_id=?',(fid,)).fetchone()
    assert row and row[1]==1,'Schedule was not enabled'
    dump=adb('shell','dumpsys','jobscheduler')
    blocks=[b for b in re.split(r'(?=JOB #)',dump) if b.startswith('JOB #') and 'FlowJobService' in b]
    (base/'evidence/jobscheduler.txt').write_text('\n'.join(blocks),encoding='utf-8')
    return row[0]

def record(name,status,timeout=25):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        launch();click('History');root=tree();node=find(root,name+' · '+status)
        if node is not None:return node
        click('Close');time.sleep(.15)
    raise AssertionError(f'{name} did not reach {status}')

assert adb('shell','getprop','ro.kernel.qemu').strip()=='1'
assert 'GhostMagic_Validation' in adb('emu','avd','name'),'Refuses other AVDs'
(base/'evidence/background-smoke.txt').unlink(missing_ok=True)
adb('install','-r',str(base/'app/build/outputs/apk/debug/app-debug.apk'))
# A fresh installed test package is expected. Never erase a user's saved flows.
adb('shell','am','force-stop',package)
token=uuid.uuid4().hex[:5]
normal_name='00 Background '+token;stop_name='01 System stop '+token;crash_name='02 Process interruption '+token
for old in (base/'evidence').glob('*.ghostflow.json'):
    doc=json.loads(old.read_text(encoding='utf-8'))
    if doc.get('name','').startswith(('00 Background ','01 System stop','02 Process interruption')):
        fid=str(uuid.UUID(doc['id']));adb('shell','run-as',package,'rm','-f','files/flows/'+fid+'.json')
normal=seed(normal_name,False);stop=seed(stop_name,True);crash=seed(crash_name,True)
jobs={}
try:
    for name,fid in [(normal_name,normal),(stop_name,stop),(crash_name,crash)]:jobs[name]=enable(name,fid)
    adb('shell','input','keyevent','3')
    adb('shell','cmd','jobscheduler','run','-f',package,str(jobs[normal_name]))
    tap(record(normal_name,'COMPLETED'));assert normal_name+' finished' in ET.tostring(tree(),encoding='unicode');image('completed');click('Close');click('Close')
    results.append('Background JobService completed a saved periodic flow with the editor hidden.')
    print(results[-1],flush=True)
    adb('shell','input','keyevent','3');adb('shell','cmd','jobscheduler','run','-f',package,str(jobs[stop_name]));record(stop_name,'RUNNING');click('Close')
    adb('shell','input','keyevent','3');adb('shell','cmd','jobscheduler','timeout',package,str(jobs[stop_name]))
    tap(record(stop_name,'CANCELLED'));assert stop_name+' finished' not in ET.tostring(tree(),encoding='unicode');image('cancelled');click('Close');click('Close')
    results.append('System job timeout cancelled an in-progress wait and did not execute its following action.')
    print(results[-1],flush=True)
    adb('shell','input','keyevent','3');adb('shell','cmd','jobscheduler','run','-f',package,str(jobs[crash_name]));record(crash_name,'RUNNING');click('Close')
    adb('shell','input','keyevent','3');pid=adb('shell','pidof',package).strip();assert re.fullmatch(r'\d+',pid),pid
    adb('shell','run-as',package,'kill','-9',pid)
    tap(record(crash_name,'INTERRUPTED'));image('interrupted');click('Close');click('Close')
    adb('shell','input','keyevent','3');adb('shell','cmd','jobscheduler','run','-f',package,str(jobs[crash_name]))
    record(crash_name,'INTERRUPTED');screen=ET.tostring(tree(),encoding='unicode');assert crash_name+' · RUNNING' not in screen;assert crash_name+' · COMPLETED' not in screen;click('Close')
    results.append('Process death left an INTERRUPTED journal record; immediate repeated delivery produced no new run.')
    print(results[-1],flush=True)
    adb('reboot');deadline=time.monotonic()+90
    while time.monotonic()<deadline:
        ready=raw('shell','getprop','sys.boot_completed',check=False)
        if ready.returncode==0 and ready.stdout.strip()==b'1':break
        time.sleep(1)
    else:raise AssertionError('Emulator reboot timed out')
    adb('shell','input','keyevent','82')
    state=adb('shell','cmd','jobscheduler','get-job-state',package,str(jobs[normal_name]))
    assert 'unknown' not in state.lower() and state.strip(),state
    (base/'evidence/job-after-reboot.txt').write_text(state,encoding='utf-8')
    record(crash_name,'INTERRUPTED');image('history-after-reboot');click('Close')
    results.append('Android retained the persisted job across reboot before opening the app; journal survived reboot.')
    print(results[-1],flush=True)
finally:
    for name in jobs:
        try:open_flow(name);click('Schedule',scroll=True);click('Disable')
        except Exception as e:print('Cleanup could not disable '+name+': '+str(e))
(base/'evidence/background-smoke.txt').write_text('\n'.join(results)+'\n',encoding='utf-8')
print('\n'.join(results))
