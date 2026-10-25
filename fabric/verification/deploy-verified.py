"""Atomically deploy one verified Smart NPC artifact to explicitly selected instances.

Defaults to a dry run. The release workflow owns provenance/upgrade-manifest
updates; this helper only validates receipts and replaces the selected mod.
"""
import argparse,hashlib,json,shutil,subprocess,time,zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

p=argparse.ArgumentParser()
p.add_argument('--workspace',type=Path,required=True)
p.add_argument('--jar',type=Path,required=True)
p.add_argument('--receipt',type=Path,required=True)
p.add_argument('--instance',action='append',required=True,help='role=workspace-relative instance directory')
p.add_argument('--execute',action='store_true')
a=p.parse_args();root=a.workspace.resolve();jar=a.jar.resolve()
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
def meta(f):
 with zipfile.ZipFile(f) as z:return json.loads(z.read('fabric.mod.json'))
r=json.loads(a.receipt.read_text(encoding='utf-8-sig'));digest=sha(jar)
assert digest==r['sha256'] and meta(jar)['id']=='smart_npc'
repo=Path(__file__).resolve().parents[2]
head=subprocess.check_output(['git','-C',str(repo),'rev-parse','HEAD'],text=True).strip()
assert head==r['sourceCommit'],'Release source commit differs'
assert not subprocess.check_output(['git','-C',str(repo),'status','--porcelain'],text=True).strip(),'Commit reviewed sources before deploying'
xml=[ET.parse(f).getroot() for f in Path(r['unitResultDirectory']).glob('TEST-*.xml')]
assert sum(int(x.get('tests')) for x in xml)>=43
assert not sum(int(x.get('failures'))+int(x.get('errors')) for x in xml)
assert r.get('runtimeCases'),'Runtime evidence is required'
for case in r['runtimeCases']:
 folder=(root/'.verification'/case['name']).resolve();folder.relative_to(root/'.verification')
 assert not(folder/'failure.txt').exists(),case['name']
 assert case.get('markers'),'A case without completion markers is not evidence'
 for marker in case['markers']:
  target=(folder/marker).resolve();target.relative_to(folder)
  assert target.is_file(),(case['name'],marker)
 assert sha(folder/'mods'/jar.name)==digest,case['name']
if 'originalWorld' in r:
 source=Path(r['originalWorld']);proof=json.loads(Path(r['worldCopyReceipt']).read_text())['fingerprints']
 assert {str(f.relative_to(source)):sha(f) for f in source.rglob('*') if f.is_file() and f.name!='session.lock'}==proof,'Original world changed; review before deploying'
# Query command lines, not just java.exe: a warm Gradle daemon may remain open.
proc=subprocess.run(['powershell','-NoProfile','-Command',"Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress"],capture_output=True,text=True,check=True)
processes=json.loads(proc.stdout or '[]') or [];processes=processes if isinstance(processes,list) else [processes]
assert not any(any(s in (x.get('CommandLine') or '') for s in ['KnotClient','KnotServer','launch.args','replay.args','server.args']) for x in processes),'Game/test clients are still running'
instances=[]
for spec in a.instance:
 role,relative=spec.split('=',1);instance=(root/relative).resolve();instance.relative_to(root/'instances')
 mods=instance/'mods';current=[f for f in mods.glob('*.jar') if meta(f).get('id')=='smart_npc']
 assert len(current)==1 and sha(current[0])==r['previousSha256'],role
 assert meta(current[0]).get('depends',{}).get('minecraft')==meta(jar).get('depends',{}).get('minecraft'),'Game target differs'
 assert not (mods/jar.name).exists()
 unchanged={str(f.relative_to(instance)):sha(f) for f in mods.glob('*.jar') if f!=current[0]}
 for f in [instance/'options.txt',*list((instance/'config').rglob('*'))]:
  if f.is_file():unchanged[str(f.relative_to(instance))]=sha(f)
 instances.append((role,instance,current[0],unchanged))
print(json.dumps({'state':'ready','artifact':jar.name,'sha256':digest,'roles':[i[0] for i in instances]},indent=2))
if not a.execute:raise SystemExit()
backup=root/'.verification'/('smartnpc-deploy-'+str(time.time_ns()));backup.mkdir()
for role,instance,old,unchanged in instances:
 (backup/role).mkdir();shutil.copy2(old,backup/role/old.name)
try:
 for role,instance,old,unchanged in instances:
  new=instance/'mods'/jar.name;temp=new.with_suffix('.pending')
  assert not temp.exists();shutil.copy2(jar,temp);assert sha(temp)==digest;temp.replace(new);old.unlink()
  assert all(sha(instance/path)==expected for path,expected in unchanged.items()),'Unrelated files changed'
except Exception:
 for role,instance,old,unchanged in instances:
  new=instance/'mods'/jar.name
  if new.exists():new.unlink()
  shutil.copy2(backup/role/old.name,old)
 raise
result={'state':'installed','artifact':jar.name,'sha256':digest,'sourceCommit':head,'roles':[i[0] for i in instances],'previousSha256':r['previousSha256'],'unrelatedJarsAndSettingsUnchanged':True,'backup':str(backup)}
(backup/'receipt.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result,indent=2))
