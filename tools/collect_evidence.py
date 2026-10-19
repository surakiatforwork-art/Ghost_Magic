"""Collect the last successful Gradle results without rerunning or masking checks."""
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

base=Path(__file__).resolve().parents[1]
evidence=base/'evidence';evidence.mkdir(exist_ok=True)
suites=sorted((base/'core/build/test-results/test').glob('TEST-*.xml'))
assert suites,'No core test results'
root=ET.Element('testsuites',{'name':'Ghost Magic core'})
totals={key:0 for key in ('tests','failures','errors','skipped')}
suite_dir=evidence/'core-test-suites';suite_dir.mkdir(exist_ok=True)
for path in suites:
    suite=ET.parse(path).getroot()
    for key in totals:totals[key]+=int(suite.get(key,'0'))
    root.append(suite)
assert totals['failures']==totals['errors']==0,totals
for key,value in totals.items():root.set(key,str(value))
android_files=sorted((base/'app/build/outputs/androidTest-results/connected/debug').glob('TEST-*.xml'))
assert len(android_files)==1,'Expected exactly one isolated Android test target'
android=ET.parse(android_files[0]).getroot();assert int(android.get('failures','0'))==int(android.get('errors','0'))==0
ET.ElementTree(root).write(evidence/'core-tests.xml',encoding='utf-8',xml_declaration=True)
for path in suites:shutil.copy2(path,suite_dir/path.name)
shutil.copy2(android_files[0],evidence/'android-tests.xml')
shutil.copy2(base/'app/build/intermediates/lint_intermediate_text_report/debug/lint-results-debug.txt',evidence/'lint.txt')
print(f"Collected {totals['tests']} core and {android.get('tests')} Android tests.")
