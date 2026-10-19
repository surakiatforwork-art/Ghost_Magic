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
    adb('shell','uiautomator','dump','/sdcard/ghost-ocr-window.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/ghost-ocr-window.xml'))
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
    remote='/sdcard/ghost-ocr-'+name+'.png';adb('shell','screencap','-p',remote);adb('pull',remote,str(base/'evidence'/('ocr-'+name+'.png')))
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


if __name__=='__main__':
    assert 'GhostMagic_Validation' in adb('emu','avd','name')
    r=tree();print(json.dumps([(n.get('text'),n.get('bounds'),n.get('class')) for n in r.iter('node') if n.get('text') or n.get('checkable')=='true'],ensure_ascii=True))
