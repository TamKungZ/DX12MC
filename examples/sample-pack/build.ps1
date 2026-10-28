$root = Resolve-Path "$PSScriptRoot\..\.."
& "$root\gradlew.bat" -p $root compileShaderPack "-PpackDir=$PSScriptRoot"
