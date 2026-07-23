[CmdletBinding()]
param(
    [string]$Developer,
    [switch]$IncludeOptional,
    [switch]$UpdateExisting
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$manifestPath = Join-Path $workspaceRoot "workspace-manifest.json"
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json

if (-not (Get-Command git -ErrorAction SilentlyContinue))
{
    throw "Git is required but was not found on PATH."
}

function Resolve-WorkspacePath
{
    param([Parameter(Mandatory = $true)][string]$RelativePath)

    if ([System.IO.Path]::IsPathRooted($RelativePath))
    {
        throw "Repository path must be relative: $RelativePath"
    }

    $candidate = [System.IO.Path]::GetFullPath((Join-Path $workspaceRoot $RelativePath))
    $prefix = $workspaceRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar

    if (-not $candidate.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase))
    {
        throw "Repository path escapes the workspace: $RelativePath"
    }

    return $candidate
}

foreach ($repository in $manifest.repositories)
{
    if (-not $repository.required -and -not $IncludeOptional)
    {
        Write-Host "Skipping optional repository $($repository.path)"
        continue
    }

    $target = Resolve-WorkspacePath -RelativePath $repository.path
    $gitPath = Join-Path $target ".git"

    if (Test-Path -LiteralPath $gitPath)
    {
        Write-Host "Found existing repository $($repository.path)"

        if ($UpdateExisting)
        {
            $status = git -C $target status --porcelain
            $branch = git -C $target branch --show-current

            if ($LASTEXITCODE -ne 0)
            {
                throw "Unable to inspect repository $($repository.path)"
            }

            if ($status)
            {
                Write-Warning "Skipping update for $($repository.path): working tree is not clean."
            }
            elseif ($branch -ne $repository.defaultBranch)
            {
                Write-Warning "Skipping update for $($repository.path): current branch is '$branch'."
            }
            else
            {
                git -C $target pull --ff-only origin $repository.defaultBranch

                if ($LASTEXITCODE -ne 0)
                {
                    throw "Unable to update repository $($repository.path)"
                }
            }
        }

        continue
    }

    if ((Test-Path -LiteralPath $target) -and (Get-ChildItem -LiteralPath $target -Force -ErrorAction Stop | Select-Object -First 1))
    {
        throw "Target exists and is not a Git repository: $target"
    }

    $parent = Split-Path -Parent $target
    New-Item -ItemType Directory -Path $parent -Force | Out-Null

    Write-Host "Cloning $($repository.url) into $($repository.path)"
    git clone --branch $repository.defaultBranch --single-branch $repository.url $target

    if ($LASTEXITCODE -ne 0)
    {
        throw "Unable to clone repository $($repository.path)"
    }
}

$developerFile = Join-Path $workspaceRoot ".trellis/.developer"

if (Test-Path -LiteralPath $developerFile)
{
    Write-Host "Trellis developer identity already exists; leaving it unchanged."
}
elseif ($Developer)
{
    if (-not (Get-Command python -ErrorAction SilentlyContinue))
    {
        throw "Python is required to initialize the Trellis developer identity."
    }

    & python (Join-Path $workspaceRoot ".trellis/scripts/init_developer.py") $Developer

    if ($LASTEXITCODE -ne 0)
    {
        throw "Unable to initialize Trellis developer identity."
    }
}
else
{
    Write-Warning "No Trellis developer identity exists. Re-run with -Developer <name>."
}

Write-Host "Workspace bootstrap complete."
