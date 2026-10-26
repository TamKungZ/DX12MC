param(
    [Parameter(Mandatory = $true)][string] $Tag,
    [string] $Changelog = 'CHANGELOG.md',
    [string] $Output
)

$ErrorActionPreference = 'Stop'

if ($Tag -notmatch '^v?(\d+\.\d+\.\d+)(\+mc\d+\.\d+(\.\d+)?)?$') {
    throw "Tag '$Tag' is not a release tag. Expected X.Y.Z+mcA.B.C or vX.Y.Z+mcA.B.C."
}
$version = $Matches[1]

$lines = [System.IO.File]::ReadAllLines((Resolve-Path -LiteralPath $Changelog).ProviderPath)

$notes = @($lines | Where-Object { $_.Contains('Note:') })

$heading = '^## ' + [regex]::Escape($version) + '( - .*)?$'
$start = -1
for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -match $heading) {
        if ($start -ge 0) { throw "CHANGELOG.md has more than one section for $version." }
        $start = $i
    }
}
if ($start -lt 0) { throw "CHANGELOG.md has no '## $version' section." }

$end = $lines.Length
for ($i = $start + 1; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -match '^## ') { $end = $i; break }
}

$section = @($lines[$start..($end - 1)])
while ($section.Count -gt 1 -and [string]::IsNullOrWhiteSpace($section[-1])) {
    $section = @($section[0..($section.Count - 2)])
}
if (@($section | Select-Object -Skip 1 | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count -eq 0) {
    throw "CHANGELOG.md section for $version is empty."
}

$result = New-Object System.Collections.Generic.List[string]
$result.Add('# Changelog')
foreach ($note in $notes) {
    $result.Add('')
    $result.Add($note)
}
$result.Add('')
foreach ($line in $section) { $result.Add($line) }

$text = ($result -join "`n") + "`n"
if ($Output) {
    $target = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Output)
    [System.IO.File]::WriteAllText($target, $text, (New-Object System.Text.UTF8Encoding($false)))
} else {
    [Console]::Out.Write($text)
}
