from pathlib import Path
import re,json,sys,xml.etree.ElementTree as ET
out=Path(sys.argv[2]); out.mkdir(exist_ok=True,parents=True)
modules=re.findall(r'include\("(:[\w:.-]+)"\)',Path('settings.gradle.kts').read_text())
assert sorted(modules)==sorted([':core:browser',':core:link',':app-phone',':app-rg']),modules
roots=[Path(m.lstrip(':').replace(':','/')) for m in modules]

def in_gate(path):
    return any(path.is_relative_to(root/'src') for root in roots)

def declarations(kind, gate=True):
    found=[]
    for path in sorted(Path('.').glob('**/src/'+kind+'/**/*')):
        if path.suffix not in ('.java','.kt') or in_gate(path)!=gate: continue
        s=re.sub(r'/\*.*?\*/','',path.read_text(),flags=re.S)
        pkg=re.search(r'^package\s+([\w.]+)',s,re.M)
        if not pkg: continue
        methods=re.findall(r'@Test(?:\([^)]*\))?\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:fun\s+(`[^`]+`|\w+)\s*\(|public\s+(?:final\s+)?void\s+(\w+)\s*\()',s)
        for kt,jv in methods:
            found.append(pkg.group(1)+'.'+path.stem+'#'+(kt or jv).strip('`'))
    assert len(found)==len(set(found)), 'duplicate test declarations'
    return sorted(found)

if sys.argv[1]=='declare':
    data={kind:declarations(kind) for kind in ('test','androidTest')}
    data['gradleProjects']=modules
    data['outsideGateStandaloneExperiments']={
        'reason':'Not included in root settings.gradle.kts; not part of plan section12 four-project host gate',
        'test':declarations('test',False),
        'androidTest':declarations('androidTest',False),
    }
    (out/'predeclared.json').write_text(json.dumps(data,indent=2))
    print('ROOT_GRADLE_PROJECTS',modules)
    print('PREDECLARED_JVM',len(data['test']))
    print('STANDALONE_EXPERIMENTS_OUTSIDE_GATE',data['outsideGateStandaloneExperiments'])
    phone=[x for x in data['androidTest'] if x.startswith('com.code2hack.eyebrowse.phone.')]
    print('PREDECLARED_PHONE',len(phone)); print('\n'.join(phone))
    assert len(phone)==36, len(phone)
elif sys.argv[1]=='reconcile':
    expected=json.loads((out/'predeclared.json').read_text())['test']
    actual=[]; failures=errors=skipped=0; suites=0
    for rootdir in roots:
        for p in rootdir.glob('build/test-results/**/TEST-*.xml'):
            root=ET.parse(p).getroot(); cases=root.findall('testcase')
            if cases: suites+=1
            failures+=int(root.get('failures','0')); errors+=int(root.get('errors','0')); skipped+=int(root.get('skipped','0'))
            actual.extend(t.get('classname')+'#'+t.get('name') for t in cases)
    data={'gradleProjects':modules,'suites':suites,'tests':len(actual),'failures':failures,'errors':errors,'skipped':skipped,
          'missing':sorted(set(expected)-set(actual)), 'unexpected':sorted(set(actual)-set(expected)),
          'duplicates':len(actual)-len(set(actual))}
    (out/'jvm-reconciliation.json').write_text(json.dumps(data,indent=2)); print(json.dumps(data,indent=2))
    assert actual and not(data['missing'] or data['unexpected'] or data['duplicates'] or failures or errors or skipped)
