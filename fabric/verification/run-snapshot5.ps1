param(
 [Parameter(Mandatory)][string]$Workspace,
 [Parameter(Mandatory)][string]$ModJar,
 [Parameter(Mandatory)][ValidatePattern('^[a-zA-Z0-9_.-]+$')][string]$TestName,
 [ValidateSet('Functional','Population','RenderBuddy','Settings','Inventory','Cabin','CookingRecovery','Food','FoodCheckpoint','FoodBurnCheckpoint','Daily','SleepRecovery','DailyContinuous','DailyNight','CookHome','HomeRepair','HomePit','HomeStorage','HomeEntry','StorageRecovery','HomeRepairChurn')][string]$Suite='Functional',
 [switch]$Replay
)
# Use the maintained installation's isolated-world runner; never copy its saves.
$ErrorActionPreference='Stop'
$workspacePath=(Resolve-Path -LiteralPath $Workspace).Path
$sourceRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$jar=(Resolve-Path -LiteralPath $ModJar).Path
$out=Join-Path $workspacePath ('.verification/'+$TestName)
if($Replay){
 if((Test-Path (Join-Path $out 'test.pid')) -and (Get-Process -Id ([int](Get-Content (Join-Path $out 'test.pid'))) -ErrorAction SilentlyContinue)){throw 'Previous fixture is still running'}
 if($Suite -notin @('Functional','Cabin','FoodCheckpoint','FoodBurnCheckpoint','Daily','DailyNight')){throw 'This fixture does not support saved-world replay'}
 if(!(Test-Path -LiteralPath (Join-Path $out 'complete.txt'))){throw 'Initial functional pass required'}
 $installedJar=Join-Path $out ('mods/'+(Split-Path $jar -Leaf))
 if((Get-FileHash -LiteralPath $installedJar).Hash -ne (Get-FileHash -LiteralPath $jar).Hash){throw 'Replay must use the same tested jar'}
 if(Test-Path -LiteralPath (Join-Path $out 'replay.args')){throw 'Use a fresh initial test for each replay'}
 $property=if($Suite -in @('Daily','DailyNight')){'"-Dsmartnpc.daily.replay=true"'}elseif($Suite -eq 'Cabin'){'"-Dsmartnpc.population.cabinReplay=true"'}elseif($Suite -in @('FoodCheckpoint','FoodBurnCheckpoint')){'"-Dsmartnpc.food.replay=true"'}else{'"-Dsmartnpcsmoke.replay=true"'}
 $world=if($Suite -in @('Daily','DailyNight')){'"SmartNpcDaily"'}elseif($Suite -eq 'Cabin'){'"SmartNpcPopulation"'}elseif($Suite -in @('FoodCheckpoint','FoodBurnCheckpoint')){'"SmartNpcFood"'}else{'"SmartNpcFunctional"'}
 if($Suite -eq 'Cabin' -and !(Test-Path (Join-Path $out 'cabin-complete.txt'))){throw 'A completed cabin is required before replay'}
 $arguments=@($property)+@(Get-Content -LiteralPath (Join-Path $out 'launch.args'))+@('"--quickPlaySingleplayer"',$world)
 [IO.File]::WriteAllLines((Join-Path $out 'replay.args'),$arguments)
 $process=Start-Process (Join-Path $workspacePath '.tools/jdk-25.0.4.1+1/bin/javaw.exe') -ArgumentList ('"@'+(Join-Path $out 'replay.args')+'"') -WorkingDirectory $out -WindowStyle Hidden -RedirectStandardOutput (Join-Path $out 'replay-stdout.log') -RedirectStandardError (Join-Path $out 'replay-stderr.log') -PassThru
 $process.Id | Set-Content (Join-Path $out 'test.pid')
 Write-Output "Saved-world replay started: $TestName"
 return
}
$extra=@()
switch($Suite){
 'SleepRecovery' {$class='SmartNpcSleepRecovery';$source=Join-Path $PSScriptRoot ($class+'.java')}
 'DailyContinuous' {$class='SmartNpcDailyContinuous';$source=Join-Path $PSScriptRoot ($class+'.java');$extra=@((Join-Path $PSScriptRoot 'SmartNpcDailySmoke.java'))}
 'DailyNight' {$class='SmartNpcDailyNight';$source=Join-Path $PSScriptRoot ($class+'.java');$extra=@((Join-Path $PSScriptRoot 'SmartNpcDailySmoke.java'))}
 'CookHome' {$class='SmartNpcCookHome';$source=Join-Path $PSScriptRoot ($class+'.java');$extra=@((Join-Path $PSScriptRoot 'SmartNpcCookingRecovery.java'))}
 'HomeRepairChurn' {$class='SmartNpcHomeRepairChurn';$source=Join-Path $PSScriptRoot ($class+'.java');$extra=@((Join-Path $PSScriptRoot 'SmartNpcDailySmoke.java'))}
 'HomeRepair' {$class='SmartNpcHomeRepair';$source=Join-Path $PSScriptRoot ($class+'.java');$extra=@((Join-Path $PSScriptRoot 'SmartNpcDailySmoke.java'))}
 'StorageRecovery' {$class='SmartNpcStorageRecovery';$source=Join-Path $PSScriptRoot ($class+'.java');$extra=@((Join-Path $PSScriptRoot 'SmartNpcDailySmoke.java'))}
 'HomeEntry' {$class='SmartNpcHomeEntry';$source=Join-Path $PSScriptRoot ($class+'.java');$extra=@((Join-Path $PSScriptRoot 'SmartNpcDailySmoke.java'))}
 'HomeStorage' {$class='SmartNpcHomeStorage';$source=Join-Path $PSScriptRoot ($class+'.java');$extra=@((Join-Path $PSScriptRoot 'SmartNpcDailySmoke.java'))}
 'HomePit' {$class='SmartNpcHomePit';$source=Join-Path $PSScriptRoot ($class+'.java');$extra=@((Join-Path $PSScriptRoot 'SmartNpcDailySmoke.java'))}
 'Daily' {$class='SmartNpcDailySmoke';$source=Join-Path $PSScriptRoot ($class+'.java')}
 'Functional' {$class='SmartNpcFunctional';$source=Join-Path $PSScriptRoot ($class+'.java');$extra=@(Get-ChildItem $PSScriptRoot 'SmartNpc*.java' | Where-Object Name -ne ($class+'.java') | ForEach-Object FullName)}
 'FoodBurnCheckpoint' {$class='SmartNpcFoodBurnCheckpoint';$source=Join-Path $PSScriptRoot ($class+'.java');$extra=@((Join-Path $PSScriptRoot 'SmartNpcFoodSmoke.java'),(Join-Path $PSScriptRoot 'SmartNpcFoodChainChecks.java'))}
 'FoodCheckpoint' {$class='SmartNpcFoodCheckpoint';$source=Join-Path $PSScriptRoot ($class+'.java');$extra=@((Join-Path $PSScriptRoot 'SmartNpcFoodSmoke.java'),(Join-Path $PSScriptRoot 'SmartNpcFoodChainChecks.java'))}
 'Food' {$class='SmartNpcFoodSmoke';$source=Join-Path $PSScriptRoot ($class+'.java');$extra=@((Join-Path $PSScriptRoot 'SmartNpcFoodChainChecks.java'))}
 'CookingRecovery' {$class='SmartNpcCookingRecovery';$source=Join-Path $PSScriptRoot ($class+'.java')}
 'Cabin' {$class='SmartNpcCabinSmoke';$source=Join-Path $PSScriptRoot ($class+'.java');$extra=@((Join-Path $PSScriptRoot 'SmartNpcPopulation.java'))}
 'Inventory' {$class='SmartNpcInventorySmoke';$source=Join-Path $PSScriptRoot ($class+'.java');$extra=@((Join-Path $PSScriptRoot 'SmartNpcPopulation.java'))}
 'Population' {$class='SmartNpcPopulation';$source=Join-Path $PSScriptRoot ($class+'.java')}
 'RenderBuddy' {$class='RenderBuddySmoke';$source=Join-Path $workspacePath 'minecraft-snapshot-mods/snapshot-5-port/RenderBuddySmoke.java'}
 'Settings' {$class='SettingsSmoke';$source=Join-Path $workspacePath 'minecraft-snapshot-mods/snapshot-5-port/SettingsSmoke.java'}
}
# A newer version can have a different filename. Exclude by actual mod identity,
# only within the disposable copy, so testing cannot load two Smart NPC versions.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$excluded=@{}
foreach($role in @{player='26.4-snapshot-5-fabric-carpet';companion='Buddy-snapshot-5'}.GetEnumerator()){
 $excluded[$role.Key]=@()
 Get-ChildItem (Join-Path $workspacePath ('instances/'+$role.Value+'/mods')) -Filter '*.jar' | ForEach-Object {
  $archive=[IO.Compression.ZipFile]::OpenRead($_.FullName)
  try{$entry=$archive.GetEntry('fabric.mod.json');if($entry){$reader=[IO.StreamReader]::new($entry.Open());try{$id=($reader.ReadToEnd() | ConvertFrom-Json).id}finally{$reader.Dispose()};if($id -eq 'smart_npc'){$excluded[$role.Key]+=$_.Name}}}finally{$archive.Dispose()}
 }
}
$source=[IO.Path]::GetRelativePath($workspacePath,$source)
$extra=@($extra | ForEach-Object {[IO.Path]::GetRelativePath($workspacePath,$_)})
& (Join-Path $workspacePath 'minecraft-snapshot-mods/snapshot-5-port/start-smoke.ps1') -Workspace $workspacePath -TestName $TestName -SmokeSource $source -SmokeClass $class -SmokeExtraSources $extra -Shaders -PreserveGraphics -ExtraMods @($jar) -ExtraBuddyMods @($jar) -ExcludeMods $excluded.player -ExcludeBuddyMods $excluded.companion
