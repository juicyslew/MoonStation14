"""Prepare a safely scoped transport manifest from the accepted R5 collection."""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOT = ROOT.parent / "Asset Building Workspace"
MANIFEST = SOURCE_ROOT / "assets/Mobs/Animals/Collection/candidates/backpack_scale_r5/export/manifest.json"
OUTPUT = ROOT / "build/nonhostile-animals-assets/scoped-r3/manifest.json"
IDS = "bat bee butterfly chick chicken_0 chicken_1 chicken_2 cockroach cow crab duck_0 duck_1 duck_2 fox frog goat goose gorilla hamster kangaroo kobold lizard monkey mothroach moproach nymph parrot penguin penguin_baby penguin_shamebrero pig possum poppy possum_old raccoon reindeer_buck reindeer_doe scurret slug snail snoth snake spider_tarantula".split()

def pascal(s):
    return ''.join(x.capitalize() for x in s.split('_'))

def main():
    rows = json.loads(MANIFEST.read_text(encoding='utf-8'))['files']
    models = {f"Animal{pascal(i)}Model.java": i for i in IDS}
    chosen, groups = [], {i: [] for i in IDS}
    # Model destinations are the authoritative class-to-appearance mapping in the collection.
    for row in rows:
        dest = Path(row['destination']).name
        if dest in models:
            groups[models[dest]].append(row)
    if any(not g for g in groups.values()):
        raise ValueError(f"Missing appearance model groups: {[k for k,v in groups.items() if not v]}")
    for ident, group in groups.items():
        base = Path(group[0]['destination']).name.removesuffix('Model.java')
        matched = [r for r in rows if Path(r['destination']).name in (base+'Model.java', base+'Animations.java') or
                   (Path(r['destination']).suffix.lower()=='.png' and
                    Path(r['destination']).parent.as_posix().endswith('/textures/entity/animals') and
                    Path(r['destination']).stem.lower() in {ident, ident+'_critical', ident+'_dead', ident+'_sleep'})]
        if not any(Path(r['destination']).name == base+'Animations.java' for r in matched):
            raise ValueError(f"Incomplete model/animation group: {ident}")
        chosen.extend(matched)
    metadata = []
    exports = {
        'r4': SOURCE_ROOT / 'assets/Mobs/Animals/Collection/candidates/source_detail_r4/export',
        'r5': SOURCE_ROOT / 'assets/Mobs/Animals/Collection/candidates/backpack_scale_r5/export',
    }
    required = {
        'r4': ['README.md', 'ATTRIBUTION.md', 'source/source_manifest.json', 'user_acceptance.json', 'export/r4_binding_contract.json'],
        'r5': ['README.md', 'ATTRIBUTION.md', 'source/source_manifest.json', 'user_acceptance.json', 'export/r5_binding_contract.json'],
    }
    for rev, export in exports.items():
        candidate = export.parent
        for rel in required[rev]:
            source = candidate / rel
            if not source.is_file():
                raise FileNotFoundError(f"Required accepted provenance missing: {source}")
            relative = source.relative_to(SOURCE_ROOT).as_posix()
            dest_rel = Path('src/main/resources/assets/moonstation14/credits/animals') / rev / rel
            row = {'file': relative, 'destination': dest_rel.as_posix(),
                   'sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                   'revision': f'accepted {rev.upper()} provenance'}
            chosen.append(row)
            metadata.append(row['destination'])
    # Keep shared Java helpers and provenance records, never unrelated animal textures.
    animal_dest = re.compile(r'Animal[^/]+(?:Model|Animations)\.java$')
    for row in rows:
        dest = row['destination']
        if dest.endswith(('.java','.txt','.md')) and not animal_dest.search(dest) and row not in chosen:
            chosen.append(row)
    destinations = set()
    unique = []
    for row in chosen:
        dest = Path(row['destination'])
        if dest.is_absolute() or '..' in dest.parts:
            raise ValueError(f"Unsafe destination: {row['destination']}")
        if row['destination'] in destinations:
            continue
        destinations.add(row['destination'])
        unique.append(row)
    chosen = unique
    chosen = [r for r in chosen if not re.search(r'(?i)mouse', r['destination'])]
    for row in chosen:
        dest = Path(row['destination'])
        source = SOURCE_ROOT / row['file']
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        if digest != row['sha256']:
            raise ValueError(f"Source hash mismatch: {row['file']}")
    if any(re.search(r'(?i)mouse', r['destination']) for r in chosen):
        raise ValueError('Mouse row included')
    # Ensure all expected appearance definitions and no other animal model definitions are present.
    selected_java = {Path(r['destination']).name for r in chosen if r['destination'].endswith('.java')}
    if sum(n.endswith('Model.java') and n.startswith('Animal') for n in selected_java) != len(IDS):
        raise ValueError('Selected model class count mismatch')
    if sum(n.endswith('Animations.java') and n.startswith('Animal') for n in selected_java) != len(IDS):
        raise ValueError('Selected animation class count mismatch')
    allowed_bases = {i + suffix for i in IDS for suffix in ('', '_critical', '_dead', '_sleep')}
    for r in chosen:
        d = Path(r['destination'])
        if d.suffix.lower() == '.png' and d.parent.as_posix().endswith('/textures/entity/animals') and d.stem.lower() not in allowed_bases:
            raise ValueError(f"Unselected animal texture included: {d}")
    # Check imports in selected animal models against existing runtime and this complete subset.
    imported = set()
    for r in chosen:
        if r['destination'].endswith('.java'):
            text = (SOURCE_ROOT / r['file']).read_text(encoding='utf-8')
            imported.update(re.findall(r'^import\s+(com\.juicyslew\.moonstation14\.[\w.]+);', text, re.M))
    source_classes = set()
    for path in (ROOT / 'src/main/java').rglob('*.java'):
        source_classes.add(path.relative_to(ROOT / 'src/main/java').as_posix().removesuffix('.java').replace('/', '.'))
    source_classes.update(Path(r['destination']).relative_to('src/main/java').as_posix().removesuffix('.java').replace('/', '.') for r in chosen if r['destination'].startswith('src/main/java/') and r['destination'].endswith('.java'))
    missing = [name for name in imported if name not in source_classes]
    if missing:
        raise ValueError(f"Unresolved imported classes: {missing}")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    data = json.dumps({'status':'SCOPED_NONHOSTILE_ANIMALS','source_base':'WORKSPACE ROOT; file paths are WORKSPACE-relative','files':chosen}, indent=2)+'\n'
    if OUTPUT.exists() and OUTPUT.read_text(encoding='utf-8') != data:
        raise FileExistsError(f"Refusing to overwrite differing artifact: {OUTPUT}")
    OUTPUT.write_text(data, encoding='utf-8')
    print(f"groups={len(groups)} rows={len(chosen)} metadata={len(metadata)} metadata_destinations={','.join(metadata)} manifest_sha256={hashlib.sha256(MANIFEST.read_bytes()).hexdigest()} output_sha256={hashlib.sha256(data.encode()).hexdigest()} output={OUTPUT.relative_to(ROOT)}")

if __name__ == '__main__': main()
