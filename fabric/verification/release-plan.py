"""Select risk-based verification without silently treating skipped suites as passes."""
import argparse,json,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--base',required=True);a=p.parse_args()
repo=Path(__file__).resolve().parents[2]
files=subprocess.check_output(['git','-C',str(repo),'diff',a.base,'--name-only'],text=True).splitlines()
files=sorted(set(files+subprocess.check_output(['git','-C',str(repo),'ls-files','--others','--exclude-standard'],text=True).splitlines()))
production=[f for f in files if '/src/main/' in f or f.endswith(('build.gradle','gradle.properties'))]
checks=[]
if production:
 checks+=['unit-and-access-widener','copied-world-headless-survival','copied-world-headless-journey-and-real-replay','final-client-login-smoke']
 if any(any(k in f for k in ['SleepAtHome','ReturnHome','BuildHouse','PlayerNpcEntity','HomeUtil','CookFood','Cooking','Furnace','SurvivalTasks','LocalPath','ManageHome','Gather']) for f in production):checks+=['DailyNight-and-real-replay','HomePit','HomeRepairChurn','Cabin-and-real-replay']
 if any(any(k in f for k in ['CookFood','Furnace','SurvivalTasks']) for f in production):checks+=['blocked-old-station','idle-owned-furnace-reuse']
 if any('GatherLogs' in f or 'SurvivalLogGather' in f for f in production):checks+=['grounded-log-timeout']
 if any(any(k in f.lower() for k in ['render','mixin','network','registry','smartnpcfabric','build.gradle']) for f in production):checks+=['RenderBuddy']
print(json.dumps({'base':a.base,'changedFiles':files,'requiredChecks':checks,'notes':['Use one warm incremental build directory; freeze each tested artifact by SHA-256','Headless tests copy a closed real world, retain all normal goals and auto-replay checkpoints','Run focused failing cases during iteration; required integration gates on the frozen release only','Unchanged suites may retain explicit baseline evidence; never label them rerun','Deploy player and Buddy together only after exact-artifact receipts pass; preserve settings and back up replaced jars']},indent=2))
