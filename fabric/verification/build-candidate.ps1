param(
 [Parameter(Mandatory)][string]$Workspace,
 [Parameter(Mandatory)][string]$JavaHome,
 [Parameter(Mandatory)][string]$StagingDirectory
)
# Warm compilation is reusable; runtime cases always receive immutable artifacts.
$ErrorActionPreference='Stop'
$workspacePath=(Resolve-Path -LiteralPath $Workspace).Path
$stagingPath=[IO.Path]::GetFullPath($StagingDirectory,$workspacePath)
$privateRoot=Join-Path $workspacePath '.verification'
if(!$stagingPath.StartsWith($privateRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Staging must be inside workspace .verification'}
New-Item -ItemType Directory $stagingPath -Force | Out-Null
$env:JAVA_HOME=(Resolve-Path -LiteralPath $JavaHome).Path
$fabric=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$timer=[Diagnostics.Stopwatch]::StartNew()
& (Join-Path $fabric 'gradlew.bat') -p $fabric --project-cache-dir (Join-Path $stagingPath 'cache') ('-PportBuildDirectory='+(Join-Path $stagingPath 'build')) '-Dorg.gradle.daemon=true' build --console=plain *> (Join-Path $stagingPath 'build.log')
if($LASTEXITCODE -ne 0){Get-Content (Join-Path $stagingPath 'build.log') -Tail 40;throw 'Candidate build/checks failed; no artifact frozen'}
$tests=0
foreach($result in Get-ChildItem (Join-Path $stagingPath 'build/test-results/test') -Filter 'TEST-*.xml'){
 $suite=([xml](Get-Content -LiteralPath $result.FullName -Raw)).testsuite
 if([int]$suite.failures -or [int]$suite.errors){throw 'Unit tests failed'}
 $tests+=[int]$suite.tests
}
if($tests -lt 43){throw 'Expected survival and journey unit suite missing'}
$jars=@(Get-ChildItem (Join-Path $stagingPath 'build/libs') -Filter '*.jar' | Where-Object Name -NotMatch '-sources|-dev')
if($jars.Count -ne 1){throw 'Expected one release jar'}
$hash=(Get-FileHash -LiteralPath $jars[0].FullName).Hash.ToLowerInvariant()
$frozen=Join-Path $stagingPath ('frozen/'+$hash)
New-Item -ItemType Directory $frozen -Force | Out-Null
$artifact=Join-Path $frozen $jars[0].Name
if(Test-Path -LiteralPath $artifact){if((Get-FileHash -LiteralPath $artifact).Hash.ToLowerInvariant() -ne $hash){throw 'Frozen artifact collision'}}else{Copy-Item -LiteralPath $jars[0].FullName -Destination $artifact}
$checks=Join-Path $frozen ('checks-'+[guid]::NewGuid().ToString('N'))
$unitResults=Join-Path $checks 'unit-results'
New-Item -ItemType Directory $unitResults | Out-Null
Get-ChildItem (Join-Path $stagingPath 'build/test-results/test') -Filter 'TEST-*.xml' | Copy-Item -Destination $unitResults
Copy-Item (Join-Path $stagingPath 'build.log') (Join-Path $checks 'build.log')
$receipt=@{state='build-verified-runtime-pending';artifact=$artifact;sha256=$hash;unitTests=$tests;unitResultDirectory=$unitResults;buildLog=(Join-Path $checks 'build.log');buildSeconds=[Math]::Round($timer.Elapsed.TotalSeconds,3)}
$receipt | ConvertTo-Json | Set-Content (Join-Path $checks 'build-receipt.json')
$receipt | ConvertTo-Json
