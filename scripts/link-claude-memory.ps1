# Links Claude Code's per-project auto-memory directory to <repo>/.claude/memory
# so memories are versioned with the code and shared across environments.
# Safe to re-run. Existing memories in the old directory are merged into the repo first.
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path.TrimEnd('\')
$target   = Join-Path $repoRoot '.claude\memory'
# Claude Code encodes the project path by replacing every non-alphanumeric char with '-'
$encoded  = $repoRoot -replace '[^A-Za-z0-9]', '-'
$projDir  = Join-Path $env:USERPROFILE ".claude\projects\$encoded"
$link     = Join-Path $projDir 'memory'

New-Item -ItemType Directory -Force $target  | Out-Null
New-Item -ItemType Directory -Force $projDir | Out-Null

if (Test-Path $link) {
    $item = Get-Item $link -Force
    if ($item.LinkType -eq 'Junction') {
        if ("$($item.Target)" -eq $target) { Write-Host "Already linked: $link -> $target"; exit 0 }
        Write-Host "Removing stale junction $link (-> $($item.Target))"
        $item.Delete()   # removes the junction only, not its contents
    } else {
        # Real directory: copy any memories the repo doesn't have yet, then move it aside.
        Get-ChildItem $link -File -Recurse | ForEach-Object {
            $rel  = $_.FullName.Substring($link.Length).TrimStart('\')
            $dest = Join-Path $target $rel
            if (-not (Test-Path $dest)) {
                New-Item -ItemType Directory -Force (Split-Path $dest) | Out-Null
                Copy-Item $_.FullName $dest
                Write-Host "Merged $rel into repo memory"
            }
        }
        $backup = "$link.bak-$(Get-Date -Format yyyyMMddHHmmss)"
        Rename-Item $link $backup
        Write-Host "Backed up old memory dir to $backup (delete it once you've confirmed the link works)"
    }
}

cmd /c mklink /J "$link" "$target" | Out-Null
Write-Host "Linked: $link -> $target"
