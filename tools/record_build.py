from pathlib import Path
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
import re

base = Path(__file__).resolve().parents[1]
version=re.search(r"versionName\s+'([^']+)'",(base/'app/build.gradle').read_text()).group(1)
apk = base / f'dist/Ghost-Magic-Android-{version}.apk'
sources = sorted([*[p for p in (base/'app/src').rglob('*') if p.is_file()],
                  *[p for p in (base/'core/src').rglob('*') if p.is_file()], base/'app/build.gradle', base/'core/build.gradle',
                  base/'build.gradle', base/'settings.gradle', base/'gradle.properties',
                  base/'gradle/wrapper/gradle-wrapper.properties'])
source_hashes = {str(p.relative_to(base)).replace('\\', '/'): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
core, android = [ET.parse(base/'evidence'/p).getroot() for p in ('core-tests.xml', 'android-tests.xml')]
assert core.get('failures') == android.get('failures') == '0'
assert core.get('errors') == android.get('errors') == '0'
with zipfile.ZipFile(apk) as z:
    helpers = [p for p in z.namelist() if p.endswith('.apk')]
    assert not helpers
record = {
    'artifact': apk.name, 'version': version, 'buildDateBangkok': '2026-10-16',
    'package': 'com.ghostmagic.android', 'minSdk': 26, 'targetSdk': 36,
    'bytes': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
    'signing': 'local Android debug key; experimental use',
    'coreTestsPassed': int(core.get('tests')), 'androidTestsPassed': int(android.get('tests')),
    'androidRuntime': 'isolated Android 16 API 36 emulator (36.1 image), x86_64',
    'backgroundSmoke': (base/'evidence/background-smoke.txt').read_text(encoding='utf-8').splitlines(),
    'backgroundSmokeBaselineVersion': '0.2.0-experimental',
    'legacyImportExportSmoke': json.loads((base/'evidence/legacy-smoke.json').read_text(encoding='utf-8')),
    'physicalDevicesTested': [], 'legacySemanticParityClaimed': False,
    'embeddedLegacyApks': helpers, 'sourceFilesSha256': source_hashes,
}
(base/'evidence/build-record.json').write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
print(f"Recorded {apk.name}: {record['coreTestsPassed']} core + {record['androidTestsPassed']} Android tests passed.")
