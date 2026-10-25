"""Fast, disposable dedicated-server tests using the installed common mod stack.

Each fresh case copies a closed world; replay reopens only that case. Never writes
to the source world. Graphics remain covered by a separate final client smoke.
"""
import argparse, hashlib, json, os, re, shutil, subprocess, time, zipfile
from pathlib import Path
from immutable_test_assets import AssetStore

p=argparse.ArgumentParser()
p.add_argument('--workspace',type=Path,required=True)
p.add_argument('--jar',type=Path,required=True)
p.add_argument('--world',type=Path)
p.add_argument('--resume-case',type=Path,help='Copy a closed prior case and its actor ledger to diagnose the saved state without replaying its whole history')
p.add_argument('--name',required=True)
p.add_argument('--distance',type=int,default=256)
p.add_argument('--mode',choices=['journey','survival','guards'],default='journey')
p.add_argument('--heading',type=int,choices=[0,90,180,270],default=0)
p.add_argument('--start-x',type=int,default=0)
p.add_argument('--start-z',type=int,default=0)
p.add_argument('--tick-rate',type=int,choices=[20,100,200,1000],default=200)
p.add_argument('--max-ticks',type=int,help='Optional shorter observation deadline for focused failures; defaults retain scenario limits')
p.add_argument('--craft-boat',action='store_true')
p.add_argument('--require-boat',action='store_true')
p.add_argument('--pause',action='store_true',help='Save with AI paused, resume after 2000 world ticks without spending the work allowance')
p.add_argument('--return-after-pause',action='store_true',help='Test saved interruption and return only, without claiming arrival at the work site')
p.add_argument('--checkpoint-aboard',action='store_true')
p.add_argument('--seed',type=int,default=0,help='Repeatable NPC identity and native behaviour RNG; terrain remains the copied world')
p.add_argument('--keep-world-conditions',action='store_true',help='Keep copied clock/weather instead of the declared dawn/clear fresh-scenario setup; saved resumes always keep their conditions')
p.add_argument('--instance',type=Path,help='Player instance; defaults to the discovered snapshot target instance')
p.add_argument('--target-metadata',type=Path,help='Upgrade target.json; otherwise match the active installed manifest')
p.add_argument('--java-home',type=Path,help='Target JDK; otherwise discover its major version under workspace .tools')
p.add_argument('--fixture-source',type=Path,help='Optional dedicated-server diagnostic/scenario Java source; frozen into the private case')
p.add_argument('--fixture-support',type=Path,action='append',default=[],help='Additional fixture Java source, frozen and compiled with the selected entry point')
p.add_argument('--replay',action='store_true')
p.add_argument('--wait',action='store_true',help='Wait for this run and automatically replay an outbound checkpoint')
a=p.parse_args();root=a.workspace.resolve();jar=a.jar.resolve()
assert a.max_ticks is None or a.max_ticks>0,'Observation deadline must be positive'
if a.resume_case:
 assert not a.replay,'Saved-state resume requires a fresh case'
 resumed=a.resume_case.resolve();assert (resumed/'actor.properties').is_file()
 a.world=resumed/'world'
assert jar.is_file(), 'Candidate jar does not exist'
assert a.name.replace('-','').replace('_','').isalnum()
case=root/'.verification'/a.name
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
def run(args):
 r=subprocess.run([str(v) for v in args],capture_output=True,text=True,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
 if r.returncode:raise RuntimeError(r.stdout+r.stderr)
if a.replay and (case/'runtime-target.json').exists():target_path=case/'runtime-target.json'
elif a.target_metadata:target_path=a.target_metadata.resolve()
else:
 setup=root/'minecraft-snapshot-mods';active=json.loads((setup/'components/active-state.json').read_text(encoding='utf-8-sig'))
 game=json.loads((setup/active['installedManifest']).read_text(encoding='utf-8-sig'))['game'];matches=[]
 for f in (root/'.verification').glob('*/target.json'):
  d=json.loads(f.read_text(encoding='utf-8-sig'))
  if d.get('requested')==game and d.get('classpath'):matches.append(f)
 assert len(matches)==1,'Supply --target-metadata when target discovery is ambiguous'
 target_path=matches[0]
target=json.loads(target_path.read_text(encoding='utf-8-sig'))
if a.java_home:jdk=a.java_home.resolve()/'bin'
else:
 major=target['java']['majorVersion'];matches=[d for d in (root/'.tools').glob('jdk-*') if (d/'release').is_file() and f'JAVA_VERSION="{major}.' in (d/'release').read_text()]
 assert len(matches)==1,'Supply --java-home for the target JDK'
 jdk=matches[0]/'bin'
cp=os.pathsep.join(target['classpath'])
instance=a.instance.resolve() if a.instance else root/'instances'/(target['requested']+'-fabric-carpet')
if not a.replay:
 assert a.world and a.world.is_dir() and not case.exists(), 'Use a fresh test name and a closed source world'
 estimated_world=sum(f.stat().st_size for f in a.world.rglob('*') if f.is_file())
 estimated_mods=sum(f.stat().st_size for f in (instance/'mods').glob('*.jar'))
 required_free=8*1024**3+2*estimated_world+4*estimated_mods+512*1024**2
 assert shutil.disk_usage(root).free>=required_free, f'Insufficient free test storage: need {required_free//1024**2} MiB including save/cache headroom; archive closed cases before launching'
 case.mkdir(parents=True)
 shutil.copy2(target_path,case/'runtime-target.json')
 (case/'mods').mkdir();(case/'classes').mkdir()
 origin=a.world.resolve();before={str(f.relative_to(origin)):sha(f) for f in origin.rglob('*') if f.is_file() and f.name!='session.lock'}
 started=time.monotonic()
 shutil.copytree(origin,case/'world',ignore=shutil.ignore_patterns('session.lock'))
 if a.resume_case:shutil.copy2(resumed/'actor.properties',case/'actor.properties')
 after={str(f.relative_to(origin)):sha(f) for f in origin.rglob('*') if f.is_file() and f.name!='session.lock'}
 assert before==after,'Source world changed during copy; do not test an inconsistent snapshot'
 (case/'world-source.json').write_text(json.dumps({'files':len(before),'bytes':sum(f.stat().st_size for f in origin.rglob('*') if f.is_file()),'copySeconds':time.monotonic()-started,'fingerprints':before},indent=2))
 assets=AssetStore(root)
 placements=[]
 def frozen_asset(source,destination):
  placement=assets.materialize(source,destination)
  placements.append({'file':str(destination.relative_to(case)),'sha256':placement.sha256,'size':placement.size,'method':placement.method})
 for f in (instance/'mods').glob('*.jar'):
  with zipfile.ZipFile(f) as z:
   meta=json.loads(z.read('fabric.mod.json'))
  if meta['id']!='smart_npc' and meta.get('environment')!='client':frozen_asset(f,case/'mods'/f.name)
 frozen_asset(jar,case/'mods'/jar.name)
 jar=case/'mods'/jar.name  # Immutable per-case bytes, even while the next incremental build runs.
 shutil.copytree(instance/'config',case/'config')
 # Absolute cache/web paths and fixed ports must not escape a disposable case.
 for config in (case/'config').rglob('*'):
  if config.is_file():
   try:content=config.read_text(encoding='utf-8-sig')
   except (UnicodeError,ValueError):continue
   isolated=content.replace(instance.as_posix(),case.as_posix()).replace(str(instance),str(case))
   if config.name=='webserver.conf' and config.parent.name=='bluemap':isolated=re.sub(r'(?m)^port\s*:\s*\d+', 'port:0',isolated)
   if isolated!=content:config.write_text(isolated,encoding='utf-8')
 bluemap=instance/'bluemap'
 if bluemap.is_dir():
  (case/'bluemap').mkdir()
  for cached in bluemap.iterdir():
   if cached.is_file() and cached.suffix in ('.jar','.zip'):frozen_asset(cached,case/'bluemap'/cached.name)
 (case/'eula.txt').write_text('eula=true\n')
 (case/'server.properties').write_text('level-name=world\nserver-port=0\nonline-mode=false\nview-distance=4\nsimulation-distance=4\npause-when-empty-seconds=-1\nmax-tick-time=120000\n')
 (case/'api').mkdir()
 for f in (case/'mods').glob('*.jar'):
  with zipfile.ZipFile(f) as z:
   if json.loads(z.read('fabric.mod.json'))['id']=='fabric-api':
    for entry in z.namelist():
     if entry.startswith('META-INF/jars/') and entry.endswith('.jar'):(case/'api'/Path(entry).name).write_bytes(z.read(entry))
 for extracted in (case/'api').glob('*.jar'):
  asset=assets.freeze(extracted)
  extracted.unlink()
  frozen_asset(asset,extracted)
 (case/'immutable-assets.json').write_text(json.dumps(placements,indent=2))
 api=list((case/'api').glob('*.jar'))
 compilecp=cp+';'+(';'.join(str(f) for f in (case/'mods').glob('*.jar')))+';'+(';'.join(map(str,api)))
 original_source=a.fixture_source.resolve() if a.fixture_source else Path(__file__).with_name('SmartNpcJourneyServer.java')
 source=case/original_source.name;shutil.copy2(original_source,source)
 support=[];support_hashes={}
 for original in a.fixture_support:
  original=original.resolve();destination=case/original.name
  assert not destination.exists(),'Fixture source basenames must be unique'
  shutil.copy2(original,destination);support.append(destination);support_hashes[destination.name]=sha(destination)
 args=['-cp',compilecp,'-d',str(case/'classes'),str(source),*map(str,support)]
 (case/'compile.args').write_text('\n'.join('"'+str(v).replace('\\','\\\\')+'"' for v in args))
 run([jdk/'javac.exe','@'+str(case/'compile.args')])
 (case/'classes/fabric.mod.json').write_text(json.dumps({'schemaVersion':1,'id':'smartnpc_journey_test','version':'1','environment':'server','entrypoints':{'server':[source.stem]}}))
 run([jdk/'jar.exe','cf',case/'mods/journey-test.jar','-C',case/'classes','.'])
 (case/'candidate.json').write_text(json.dumps({'sha256':sha(jar),'artifact':jar.name,'distance':a.distance,'tickRate':a.tick_rate,'maxTicks':a.max_ticks,'mode':a.mode,'heading':a.heading,'craftBoat':a.craft_boat,'requireBoat':a.require_boat,'seed':a.seed,'pause':a.pause,'checkpointAboard':a.checkpoint_aboard,'fixtureSha256':sha(source),'supportSourceHashes':support_hashes,'resumedCase':resumed.name if a.resume_case else None,'freshConditions':'copied' if a.keep_world_conditions else 'dawn-clear' if not a.resume_case and not a.fixture_source else None},indent=2))
else:
 assert (case/'checkpoint.txt').exists() or (case/'return-checkpoint.txt').exists()
 assert not (case/('return-replay.args' if (case/'return-checkpoint.txt').exists() else 'replay.args')).exists(), 'Checkpoint already replayed'
 assert json.loads((case/'candidate.json').read_text())['sha256']==sha(jar)
 assert json.loads((case/'candidate.json').read_text())['distance']==a.distance
args=['-Xmx4G','-XX:+UseZGC','--enable-native-access=ALL-UNNAMED','-XX:StackShadowPages=32',
 '-XX:CompileCommand=exclude,net.minecraft.resources.ResourceManagerRegistryLoadTask::lambda$load$2',
 '-Djourney.distance='+str(a.distance),'-Djourney.replay='+str(a.replay or bool(a.resume_case)).lower(),'-Djourney.mode='+a.mode,'-Djourney.heading='+str(a.heading),'-Djourney.x='+str(a.start_x),'-Djourney.z='+str(a.start_z),'-Djourney.tickRate='+str(a.tick_rate),'-Djourney.craftBoat='+str(a.craft_boat).lower(),'-Djourney.requireBoat='+str(a.require_boat).lower(),
 '-Djourney.pause='+str(a.pause).lower(),'-Djourney.aboard='+str(a.checkpoint_aboard).lower(),'-Djourney.seed='+str(a.seed),'-Djourney.returnAfterPause='+str(a.return_after_pause).lower(),
 '-Djourney.retryBlocked='+str(bool(a.resume_case) and a.mode=='journey').lower(),
 '-Djourney.fixedConditions='+str(not a.keep_world_conditions).lower(),
 '-cp',cp,'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui']
return_replay=a.replay and (case/'return-checkpoint.txt').exists()
if a.max_ticks is not None:args.insert(0,'-Djourney.maxTicks='+str(a.max_ticks))
argfile=case/('return-replay.args' if return_replay else 'replay.args' if a.replay else 'server.args')
argfile.write_text('\n'.join('"'+str(v).replace('\\','\\\\')+'"' for v in args))
prefix='return-replay-' if return_replay else 'replay-' if a.replay else ''
proc=subprocess.Popen([str(jdk/'java.exe'),'@'+str(argfile)],cwd=case,stdout=(case/(prefix+'stdout.log')).open('w'),stderr=(case/(prefix+'stderr.log')).open('w'),creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
(case/'test.pid').write_text(str(proc.pid))
print(json.dumps({'pid':proc.pid,'case':str(case),'candidateSha256':sha(jar)}))
if a.wait:
 try:code=proc.wait(timeout=900)
 except subprocess.TimeoutExpired:raise RuntimeError('Test exceeded 15 minutes; process left intact for diagnosis')
 if (case/'failure.txt').exists():raise RuntimeError((case/'failure.txt').read_text())
 if ((case/'checkpoint.txt').exists() or (case/'return-checkpoint.txt').exists()) and not(case/'complete.txt').exists() and not return_replay:
  replay_args=[os.sys.executable,Path(__file__).resolve(),'--workspace',root,'--jar',jar,'--name',a.name,'--distance',a.distance,'--mode',a.mode,'--heading',a.heading,'--start-x',a.start_x,'--start-z',a.start_z,'--tick-rate',a.tick_rate,'--replay','--wait']
  if a.craft_boat:replay_args+=['--craft-boat']
  if a.require_boat:replay_args+=['--require-boat']
  if a.pause:replay_args+=['--pause']
  if a.return_after_pause:replay_args+=['--return-after-pause']
  if a.checkpoint_aboard:replay_args+=['--checkpoint-aboard']
  replay_args+=['--seed',a.seed]
  if a.java_home:replay_args+=['--java-home',a.java_home.resolve()]
  if a.instance:replay_args+=['--instance',instance]
  if a.max_ticks is not None:replay_args+=['--max-ticks',a.max_ticks]
  run(replay_args)
 else:
  assert (case/'complete.txt').exists(),f'Process exited {code} without a completion receipt'
  print((case/'complete.txt').read_text())
