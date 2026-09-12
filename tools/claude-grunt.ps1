[CmdletBinding()]
param(
    [Parameter(Mandatory, Position = 0)]
    [string] $Task,

    [ValidateSet('Report', 'Edit')]
    [string] $Mode = 'Report',

    [ValidateSet('low', 'medium', 'high')]
    [string] $Effort = 'low',

    [string] $Model = 'sonnet',

    [string] $OutputPath
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

$install = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.vscode\extensions') -Directory `
    -Filter 'anthropic.claude-code-*-win32-x64' -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending |
    Select-Object -First 1
if (!$install) {
    throw 'Claude Code was not found in the VS Code extensions directory.'
}
$claude = Join-Path $install.FullName 'resources\native-binary\claude.exe'
if (!(Test-Path -LiteralPath $claude)) {
    throw "Claude Code binary was not found at $claude"
}

if (!$OutputPath) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutputPath = Join-Path $repo ".agent-handoffs\claude\$stamp.md"
} elseif (![System.IO.Path]::IsPathRooted($OutputPath)) {
    $OutputPath = Join-Path $repo $OutputPath
}
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutputPath) | Out-Null

$modeInstruction = if ($Mode -eq 'Edit') {
    'MODE: EDIT. Make only the explicitly requested mechanical edits. Follow CLAUDE.md.'
} else {
    'MODE: REPORT. Do not modify files. Follow CLAUDE.md.'
}
$prompt = @"
$modeInstruction

TASK:
$Task

Return a compact handoff for Codex. Cite exact files, lines, methods, commands, and log timestamps. Separate observations from inference.
"@

$arguments = @(
    '--print',
    '--model', $Model,
    '--effort', $Effort,
    '--output-format', 'text',
    '--no-session-persistence',
    '--permission-mode', $(if ($Mode -eq 'Edit') { 'acceptEdits' } else { 'plan' }),
    $(if ($Mode -eq 'Report') { '--allowed-tools=Read,Glob,Grep' } else { '--tools=default' }),
    $prompt
)

Push-Location $repo
try {
    $result = & $claude @arguments 2>&1
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

$header = @"
# Claude handoff

- Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')
- Mode: $Mode
- Effort: $Effort
- Task: $Task
- Exit code: $exitCode

"@
[System.IO.File]::WriteAllText($OutputPath, $header + ($result -join "`r`n") + "`r`n",
    [System.Text.UTF8Encoding]::new($false))

if ($exitCode -ne 0) {
    throw "Claude exited with code $exitCode. Output saved to $OutputPath"
}
Write-Output $OutputPath
