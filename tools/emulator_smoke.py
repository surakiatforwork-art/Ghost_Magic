"""UI smoke test on this project's disposable emulator only (ADB required)."""
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

base = Path(__file__).resolve().parents[1]
serial = 'emulator-5582'

def adb(*args):
    return subprocess.run(['adb', '-s', serial, *args], check=True, capture_output=True).stdout.decode('utf-8', errors='replace')

def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/ghost-smoke-window.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/ghost-smoke-window.xml'))

def click(label):
    for n in tree().iter('node'):
        if n.get('text','').casefold() == label.casefold():
            x1,y1,x2,y2 = map(int, re.findall(r'\d+', n.get('bounds')))
            if x2 > x1 and y2 > y1:
                adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
                return
    raise AssertionError(f'Visible button not found: {label}')

def screenshot(name):
    remote = f'/sdcard/ghost-smoke-{name}.png'
    adb('shell', 'screencap', '-p', remote)
    adb('pull', remote, str(base/'evidence'/f'{name}.png'))

assert adb('shell', 'getprop', 'ro.kernel.qemu').strip() == '1', 'Requires emulator'
assert 'GhostMagic_Validation' in adb('emu', 'avd', 'name'), 'Requires isolated project AVD'
adb('install', '-r', str(base/'app/build/outputs/apk/debug/app-debug.apk'))
adb('shell', 'am', 'force-stop', 'com.ghostmagic.android')
adb('shell', 'am', 'start', '-W', '-n', 'com.ghostmagic.android/.MainActivity')
tree()
screenshot('home')
click('Open flow')
tree()
screenshot('graph')
results = []
try:
    for level, expected, name in [(20, '30% or below', 'trace-low-battery'), (80, 'above 30%', 'trace-high-battery')]:
        adb('shell', 'dumpsys', 'battery', 'set', 'level', str(level))
        click('Run')
        screen = ET.tostring(tree(), encoding='unicode')
        assert 'COMPLETED' in screen and expected in screen, screen
        screenshot(name)
        results.append(f'Battery {level}%: completed expected branch')
        click('Close')
finally:
    adb('shell', 'dumpsys', 'battery', 'reset')
(base/'evidence/ui-smoke.txt').write_text('\n'.join(results)+'\n', encoding='utf-8')
print('\n'.join(results))
