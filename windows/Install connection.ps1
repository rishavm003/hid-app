$ErrorActionPreference = 'Stop'
$toolsDirectory = Join-Path $PSScriptRoot 'tools'
New-Item -ItemType Directory -Path $toolsDirectory -Force | Out-Null
$release = Invoke-RestMethod -Uri 'https://api.github.com/repos/cloudflare/cloudflared/releases/latest' -Headers @{ 'User-Agent' = 'Haven-Setup' }
$asset = $release.assets | Where-Object { $_.name -eq 'cloudflared-windows-amd64.exe' } | Select-Object -First 1
if (-not $asset -or -not $asset.digest -or -not $asset.digest.StartsWith('sha256:')) {
    throw 'The official release did not include a SHA256 digest. Download was not executed.'
}
$downloadPath = Join-Path $toolsDirectory 'cloudflared.download'
$binaryPath = Join-Path $toolsDirectory 'cloudflared.exe'
Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $downloadPath
$actual = (Get-FileHash -LiteralPath $downloadPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $asset.digest.Substring(7).ToLowerInvariant()) { throw 'Checksum verification failed. Helper was not installed.' }
Move-Item -LiteralPath $downloadPath -Destination $binaryPath -Force
Write-Output 'Connection helper installed and checksum verified. Double-click Start Haven.vbs.'
