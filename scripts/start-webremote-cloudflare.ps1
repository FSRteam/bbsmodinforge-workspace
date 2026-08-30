#Requires -Version 5.1

<#
.SYNOPSIS
为 BBS Web Remote 启动一次性 Cloudflare Quick Tunnel。

.DESCRIPTION
脚本会检查本机 Web Remote，启动 cloudflared，自动提取 trycloudflare.com
临时地址，并给出 Web Remote 必须填写的安全设置。关闭脚本窗口或按 Ctrl+C
会同时关闭临时隧道。

.PARAMETER OriginUrl
Web Remote 的本机地址。默认：http://127.0.0.1:27165

.PARAMETER CloudflaredPath
cloudflared.exe 的路径。留空时自动查找。

.PARAMETER StartupTimeoutSec
等待 Cloudflare 分配临时地址的秒数。默认 45 秒。

.PARAMETER NoBrowser
生成地址后不自动打开浏览器。

.PARAMETER NoClipboard
生成地址后不自动复制到剪贴板。

.EXAMPLE
pwsh -File .\scripts\start-webremote-cloudflare.ps1

.EXAMPLE
pwsh -File .\scripts\start-webremote-cloudflare.ps1 -OriginUrl http://127.0.0.1:28080 -NoBrowser
#>

[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string] $OriginUrl = "http://127.0.0.1:27165",

    [Parameter()]
    [string] $CloudflaredPath,

    [Parameter()]
    [ValidateRange(10, 180)]
    [int] $StartupTimeoutSec = 45,

    [Parameter()]
    [switch] $NoBrowser,

    [Parameter()]
    [switch] $NoClipboard
)

$ErrorActionPreference = "Stop"

function Write-Step
{
    param([string] $Message)

    Write-Host ("[进行中] " + $Message) -ForegroundColor Cyan
}

function Write-Success
{
    param([string] $Message)

    Write-Host ("[成功] " + $Message) -ForegroundColor Green
}

function Write-WarningMessage
{
    param([string] $Message)

    Write-Host ("[提醒] " + $Message) -ForegroundColor Yellow
}

function Resolve-Cloudflared
{
    param([string] $RequestedPath)

    if ($RequestedPath)
    {
        if (-not (Test-Path -LiteralPath $RequestedPath -PathType Leaf))
        {
            throw "找不到 cloudflared：$RequestedPath"
        }

        return (Resolve-Path -LiteralPath $RequestedPath).Path
    }

    $command = Get-Command "cloudflared.exe" -ErrorAction SilentlyContinue

    if ($command)
    {
        return $command.Source
    }

    $candidates = @()

    if (${env:ProgramFiles(x86)})
    {
        $candidates += Join-Path ${env:ProgramFiles(x86)} "cloudflared\cloudflared.exe"
    }
    if ($env:ProgramFiles)
    {
        $candidates += Join-Path $env:ProgramFiles "cloudflared\cloudflared.exe"
    }
    if ($env:LOCALAPPDATA)
    {
        $candidates += Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Links\cloudflared.exe"
    }
    if ($env:USERPROFILE)
    {
        $candidates += Join-Path $env:USERPROFILE "scoop\apps\cloudflared\current\cloudflared.exe"
    }

    foreach ($candidate in $candidates)
    {
        if (Test-Path -LiteralPath $candidate -PathType Leaf)
        {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw "没有找到 cloudflared.exe。请先安装 cloudflared，或用 -CloudflaredPath 指定路径。"
}

function Test-LocalPort
{
    param(
        [string] $HostName,
        [int] $Port,
        [int] $TimeoutMs = 1500
    )

    $client = [System.Net.Sockets.TcpClient]::new()

    try
    {
        $task = $client.ConnectAsync($HostName, $Port)

        return $task.Wait($TimeoutMs) -and $client.Connected
    }
    catch
    {
        return $false
    }
    finally
    {
        $client.Dispose()
    }
}

function Get-LogText
{
    param([string[]] $Paths)

    $parts = foreach ($path in $Paths)
    {
        if (Test-Path -LiteralPath $path -PathType Leaf)
        {
            Get-Content -Raw -LiteralPath $path -ErrorAction SilentlyContinue
        }
    }

    return ($parts -join [Environment]::NewLine)
}

function Stop-TunnelProcess
{
    param([System.Diagnostics.Process] $Process)

    if (-not $Process -or $Process.HasExited)
    {
        return
    }

    Write-Step "正在关闭一次性隧道……"
    Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
    $null = $Process.WaitForExit(5000)
}

try
{
    $origin = [Uri] $OriginUrl
}
catch
{
    throw "本机地址格式不正确：$OriginUrl"
}

if ($origin.Scheme -notin @("http", "https") -or -not $origin.Host)
{
    throw "本机地址必须是完整的 http:// 或 https:// 地址。"
}

if ($origin.UserInfo -or $origin.Query -or $origin.Fragment -or $origin.AbsolutePath -ne "/")
{
    throw "本机地址只能包含协议、主机和端口，不能包含账号、路径、查询参数或片段。"
}

$loopbackHosts = @("127.0.0.1", "localhost", "::1")

if ($origin.Host -notin $loopbackHosts)
{
    throw "为避免误公开其他服务，本脚本只接受 127.0.0.1、localhost 或 ::1。"
}

$originPort = if ($origin.IsDefaultPort)
{
    if ($origin.Scheme -eq "https") { 443 } else { 80 }
}
else
{
    $origin.Port
}

$cloudflared = Resolve-Cloudflared $CloudflaredPath
$cloudflaredVersion = (& $cloudflared --version 2>&1 | Select-Object -First 1)

Write-Host ""
Write-Host "BBS Web Remote · Cloudflare 一次性隧道" -ForegroundColor White
Write-Host "----------------------------------------" -ForegroundColor DarkGray
Write-Host ("本机地址：" + $origin.AbsoluteUri)
Write-Host ("Cloudflared：" + $cloudflared)
Write-Host ("版本：" + $cloudflaredVersion)
Write-Host ""

if (Test-LocalPort -HostName $origin.Host -Port $originPort)
{
    Write-Success "已检测到 Web Remote 正在监听 $($origin.Host):$originPort。"
}
else
{
    Write-WarningMessage "目前没有检测到 Web Remote。隧道仍会启动；请随后在游戏的 FSR 设置中启动 Web Remote。"
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
$stdoutLog = Join-Path $env:TEMP ("bbs-webremote-cloudflare-" + $stamp + ".out.log")
$stderrLog = Join-Path $env:TEMP ("bbs-webremote-cloudflare-" + $stamp + ".err.log")
$tunnelProcess = $null

try
{
    Write-Step "正在向 Cloudflare 申请一次性公网地址……"

    $arguments = @(
        "tunnel",
        "--no-autoupdate",
        "--url", $origin.AbsoluteUri,
        "--http-host-header", $origin.Authority,
        "--loglevel", "info"
    )

    $tunnelProcess = Start-Process `
        -FilePath $cloudflared `
        -ArgumentList $arguments `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -WindowStyle Hidden `
        -PassThru

    $deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSec)
    $publicUrl = $null
    $urlPattern = "https://[a-z0-9-]+\.trycloudflare\.com"

    while ([DateTime]::UtcNow -lt $deadline -and -not $tunnelProcess.HasExited)
    {
        Start-Sleep -Milliseconds 250
        $logText = Get-LogText @($stdoutLog, $stderrLog)
        $match = [regex]::Match($logText, $urlPattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)

        if ($match.Success)
        {
            $publicUrl = $match.Value.TrimEnd("/")
            break
        }
    }

    if (-not $publicUrl)
    {
        $logText = Get-LogText @($stdoutLog, $stderrLog)
        $tail = ($logText -split "`r?`n" | Select-Object -Last 18) -join [Environment]::NewLine

        if ($tunnelProcess.HasExited)
        {
            throw "cloudflared 已提前退出（退出码 $($tunnelProcess.ExitCode)）。`n$tail"
        }

        throw "等待 $StartupTimeoutSec 秒后仍未取得临时地址。`n$tail"
    }

    Write-Host ""
    Write-Success "一次性隧道已经启动。"
    Write-Host ""
    Write-Host "公网地址：" -ForegroundColor White -NoNewline
    Write-Host $publicUrl -ForegroundColor Green
    Write-Host ""

    if (-not $NoClipboard)
    {
        try
        {
            Set-Clipboard -Value $publicUrl
            Write-Success "公网地址已复制到剪贴板。"
        }
        catch
        {
            Write-WarningMessage "无法自动复制地址，请手动复制上方公网地址。"
        }
    }

    Write-Host ""
    Write-Host "请在游戏内打开：FSR 设置 > Web Remote，并填写：" -ForegroundColor Yellow
    Write-Host "  允许局域网访问：开启"
    Write-Host ("  绑定地址：" + $origin.Host)
    Write-Host ("  Host 白名单：" + $origin.Host)
    Write-Host ("  公网访问地址：" + $publicUrl)
    Write-Host "  然后点击“应用并启动”，再点击“新配对码”。"
    Write-Host ""
    Write-WarningMessage "临时地址本身不是密码。请只把地址和一次性配对码发给可信的人。"
    Write-WarningMessage "隧道窗口关闭后地址立即失效；下次启动会得到不同地址。"

    if (-not $NoBrowser)
    {
        try
        {
            Start-Process $publicUrl
            Write-Success "已在默认浏览器中打开公网地址。"
        }
        catch
        {
            Write-WarningMessage "无法自动打开浏览器，请手动打开上方公网地址。"
        }
    }

    Write-Host ""
    Write-Host "隧道正在运行。按 Ctrl+C 或关闭此窗口即可停止。" -ForegroundColor Cyan

    while (-not $tunnelProcess.HasExited)
    {
        Start-Sleep -Seconds 1
    }

    throw "cloudflared 已意外退出（退出码 $($tunnelProcess.ExitCode)）。"
}
finally
{
    Stop-TunnelProcess $tunnelProcess
    Remove-Item -LiteralPath $stdoutLog, $stderrLog -Force -ErrorAction SilentlyContinue
}
