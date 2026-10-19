"""Track every legacy resource candidate without inventing feature parity."""
from pathlib import Path
import json

base = Path(__file__).resolve().parents[1]
source = base.parents[1] / 'Automagics blue print/evidence/generated/capabilities.json'
inventory = json.loads(source.read_text(encoding='utf-8-sig'))
analogues = {
    ('trigger', 'periodic_timer_inexact'): ('trigger.periodic', 'Deferrable JobScheduler intervals >=15 minutes; legacy timing, time-range filters and execution policy are not parity-verified.'),
    ('action', 'set_screen_brightness'): ('action.brightness', 'Partial manual-brightness subset; special access required. No automatic-mode override or legacy parity claim.'),
    ('action', 'set_wifi_state'): ('action.wifi.panel', 'User-assisted redesign only. Opening the panel does not implement the legacy toggle.'),
    ('action', 'notification_status_bar'): ('action.notification', 'Simple own-app notification subset; legacy IDs, buttons and advanced semantics are not migrated.'),
    ('action', 'write_to_log'): ('action.log', 'Run-local trace analogue; not the legacy persistent log.'),
    ('action', 'sleep'): ('action.delay', 'Bounded visible-lifecycle delay; no durable or device-sleep semantics.'),
    ('condition', 'battery_level'): ('condition.battery.above', 'Strict greater-than percentage subset; legacy modes need real fixture validation.'),
}
rows = []
for kind, category in inventory['categories'].items():
    for legacy_type in category['logicalVariants']:
        candidate = analogues.get((kind, legacy_type))
        rows.append({
            'kind': kind, 'legacyResourceVariant': legacy_type,
            'assessment': 'partial-analogue' if candidate else 'unassessed',
            'newType': candidate[0] if candidate else None,
            'semanticParityVerified': False,
            'notes': candidate[1] if candidate else 'Requires evidence, API compatibility review, implementation and regression flows.'
        })
out = {
    'schemaVersion': 1, 'legacySource': inventory['source'],
    'scope': 'Resource-derived candidates, not confirmed executable node types. This is a backlog, not an importer mapping.',
    'candidateCount': len(rows),
    'assessedPartialAnalogues': sum(r['assessment'] == 'partial-analogue' for r in rows),
    'verifiedSemanticParityCount': 0,
    'entries': rows,
}
(base/'docs/migration-ledger.json').write_text(json.dumps(out, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print(f"Tracked {len(rows)} resource variants; {out['assessedPartialAnalogues']} partial analogues; zero verified legacy parity claims.")
