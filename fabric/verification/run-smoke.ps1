param(
    [Parameter(Mandatory)][string]$InstanceDirectory,
    [Parameter(Mandatory)][string]$LaunchTemplate,
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [switch]$Replay,
    [switch]$FullModStack
)
# Uses a local offline launcher argument file for classpath/assets. Never copies saves.
$ErrorActionPreference = 'Stop'
$output = [IO.Path]::GetFullPath($OutputDirectory)
if ((Test-Path -LiteralPath $output) -and !$Replay) { throw 'Use a new output directory, or -Replay for the saved test world.' }
if ($Replay -and !(Test-Path -LiteralPath (Join-Path $output 'npc-uuid.txt'))) { throw 'No completed initial test to replay.' }
if ($Replay) {
    $history = Join-Path $output ('previous-run-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    $null = New-Item -ItemType Directory -Path $history
    foreach ($name in @('failure.txt','replay-complete.txt','stdout.log','stderr.log')) {
        $previous = Join-Path $output $name
        if (Test-Path -LiteralPath $previous) { Move-Item -LiteralPath $previous -Destination (Join-Path $history $name) }
    }
}
foreach ($directory in @('mods','config','classes','compile-libs','shaderpacks')) {
    $null = New-Item -ItemType Directory -Force (Join-Path $output $directory)
}
$patterns = if ($FullModStack) { @('*.jar') } else { @('fabric-api-*.jar','sodium-*.jar','iris-*.jar') }
foreach ($pattern in $patterns) {
    Get-ChildItem (Join-Path $InstanceDirectory 'mods') -Filter $pattern |
        Where-Object Name -NotLike 'SmartNpc-*' | Copy-Item -Destination (Join-Path $output 'mods') -Force
}
$modVersion = (Get-Content (Join-Path $PSScriptRoot '../gradle.properties') | Where-Object { $_ -match '^mod_version=' }) -replace '^mod_version=', ''
$modJar = Get-Item (Join-Path $PSScriptRoot "../build/libs/SmartNpc-Fabric-$modVersion.jar")
if (!$modJar) { throw 'Build the Fabric module first.' }
Copy-Item -LiteralPath $modJar.FullName -Destination (Join-Path $output 'mods') -Force
if ($FullModStack) {
    $irisConfig = Join-Path $InstanceDirectory 'config/iris.properties'
    if (Test-Path $irisConfig) { Copy-Item $irisConfig (Join-Path $output 'config') -Force }
    Get-ChildItem (Join-Path $InstanceDirectory 'shaderpacks') -File | Copy-Item -Destination (Join-Path $output 'shaderpacks') -Force
}
$launchArgs = [Collections.Generic.List[string]]::new()
Get-Content -LiteralPath $LaunchTemplate | ForEach-Object { $launchArgs.Add($_) }
$cpIndex = $launchArgs.IndexOf('"-cp"') + 1
if (!$cpIndex) { throw 'Launch template has no quoted -cp argument.' }
$classpath = $launchArgs[$cpIndex].Trim('"').Replace('\\','\')
Add-Type -AssemblyName System.IO.Compression.FileSystem
$api = Get-ChildItem (Join-Path $output 'mods') -Filter 'fabric-api-*.jar' | Select-Object -First 1
$zip = [IO.Compression.ZipFile]::OpenRead($api.FullName)
try {
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName.StartsWith('META-INF/jars/') -and $entry.Name.EndsWith('.jar')) {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $output "compile-libs/$($entry.Name)"),$true)
        }
    }
} finally { $zip.Dispose() }
$compileClasspath = "$classpath;$($modJar.FullName);" + ((Get-ChildItem (Join-Path $output 'compile-libs/*.jar')).FullName -join ';')
& (Join-Path $JavaHome 'bin/javac.exe') -cp $compileClasspath -d (Join-Path $output 'classes') (Join-Path $PSScriptRoot 'SmartNpcFunctional.java')
if ($LASTEXITCODE) { throw 'Smoke harness compilation failed.' }
[IO.File]::WriteAllText((Join-Path $output 'classes/fabric.mod.json'),'{"schemaVersion":1,"id":"smartnpc_functional","version":"1","environment":"client","entrypoints":{"client":["SmartNpcFunctional"]}}')
& (Join-Path $JavaHome 'bin/jar.exe') cf (Join-Path $output 'mods/smartnpc-functional-test.jar') -C (Join-Path $output 'classes') .
if ($LASTEXITCODE) { throw 'Smoke harness packaging failed.' }
foreach ($pair in @(@('--gameDir',$output),@('--width','1280'),@('--height','720'),@('--accessToken','0'),@('--username','SmartNpcTest'))) {
    $index = $launchArgs.IndexOf('"'+$pair[0]+'"') + 1
    if (!$index) { throw "Missing argument $($pair[0])" }
    $launchArgs[$index] = '"'+$pair[1].Replace('\','\\')+'"'
}
$quick = $launchArgs.IndexOf('"--quickPlaySingleplayer"')
if ($quick -ge 0) { $launchArgs.RemoveAt($quick+1); $launchArgs.RemoveAt($quick) }
if ($Replay) {
    $launchArgs.Add('"--quickPlaySingleplayer"'); $launchArgs.Add('"SmartNpcFunctional"')
    $launchArgs.Insert(0,'"-Dsmartnpcsmoke.replay=true"')
}
for ($i=0;$i -lt $launchArgs.Count;$i++) { if ($launchArgs[$i] -match '^"-Xmx') { $launchArgs[$i]='"-Xmx8G"' } }
# Work around a JVM crash observed with this snapshot's registry loader on Java 25.
$launchArgs.Insert(0,'"-XX:CompileCommand=exclude,net.minecraft.resources.ResourceManagerRegistryLoadTask::lambda$load$2"')
$launchPath = Join-Path $output 'launch.args'
[IO.File]::WriteAllLines($launchPath,$launchArgs)
$options = [IO.File]::ReadAllText((Join-Path $InstanceDirectory 'options.txt'))
foreach ($pair in @(@('preferredGraphicsBackend','"opengl"'),@('onboardAccessibility','false'),@('tutorialStep','none'),@('enableVsync','false'),@('maxFps','60'),@('pauseOnLostFocus','false'),@('fullscreen','false'),@('soundCategory_master','0.0'),@('renderDistance','8'),@('simulationDistance','8'),@('guiScale','2'))) {
    $options = $options -replace ('(?m)^'+$pair[0]+':.*$'),($pair[0]+':'+$pair[1])
}
[IO.File]::WriteAllText((Join-Path $output 'options.txt'),$options)
$process = Start-Process (Join-Path $JavaHome 'bin/javaw.exe') -ArgumentList ('"@'+$launchPath+'"') -WorkingDirectory $output -WindowStyle Hidden -RedirectStandardOutput (Join-Path $output 'stdout.log') -RedirectStandardError (Join-Path $output 'stderr.log') -PassThru
$process.Id | Set-Content (Join-Path $output 'test.pid')
Write-Output "Isolated Smart NPC test PID $($process.Id); results: $output"
