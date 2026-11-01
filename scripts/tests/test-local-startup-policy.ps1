$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$parseTokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
    (Join-Path $root 'scripts/start-all.ps1'), [ref]$parseTokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Startup script does not parse.' }
foreach ($name in @('Assert-File', 'Build-LatestBackend')) {
    $definition = $ast.Find({ param($node)
        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
        $node.Name -eq $name
    }, $true)
    if (-not $definition) { throw "Missing function: $name" }
    . ([scriptblock]::Create($definition.Extent.Text))
}
$source = $ast.Extent.Text
foreach ($property in @(
    '--spring.flyway.enabled=false', '--spring.liquibase.enabled=false',
    '--spring.jpa.hibernate.ddl-auto=validate', '--spring.sql.init.mode=never'
)) {
    if (-not $source.Contains("'$property'")) { throw "Missing protection: $property" }
}
if ($source -match '-m\s+alembic|spring-boot:run') { throw 'Migration or stale-code fallback remains.' }
if ($source.IndexOf('$jar = Build-LatestBackend') -gt $source.IndexOf("Start-LoggedProcess 'f1'")) {
    throw 'Build must finish before any service starts.'
}
Write-Output 'PASS startup policy disables migrations and builds before launching services'

# Exercise the actual build function with fake wrappers, never a database or local configuration.
$temporary = Join-Path ([IO.Path]::GetTempPath()) ('lesson startup test ' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $temporary 'target') -Force | Out-Null
$jar = Join-Path $temporary 'target/Lesoongen-0.0.1-SNAPSHOT.jar'
$wrapper = Join-Path $temporary 'mvnw.cmd'
$originalDirectory = (Get-Location).Path
try {
    Set-Content -LiteralPath $jar -Value 'stale artifact'
    Set-Content -LiteralPath $wrapper -Encoding Ascii -Value "@echo off`r`nexit /b 1"
    $failed = $false
    try { Build-LatestBackend $temporary | Out-Null } catch {
        $failed = $_.Exception.Message -like 'Java build failed*'
    }
    if (-not $failed) { throw 'Failed build must reject an existing stale JAR.' }
    Write-Output 'PASS build failure cannot reuse a stale artifact'

    Set-Content -LiteralPath $wrapper -Encoding Ascii -Value @'
@echo off
if not "%~1"=="-DskipTests" exit /b 7
if not "%~2"=="package" exit /b 8
echo fresh artifact>target\Lesoongen-0.0.1-SNAPSHOT.jar
exit /b 0
'@
    $built = Build-LatestBackend $temporary
    if ($built -ne $jar.Replace('/', '\') -or (Get-Content -LiteralPath $built -Raw).Trim() -ne 'fresh artifact') {
        throw 'Build must return the freshly packaged application JAR.'
    }
    Write-Output 'PASS successful build uses the new artifact, including paths with spaces'

    Remove-Item -LiteralPath $jar
    Set-Content -LiteralPath $wrapper -Encoding Ascii -Value "@echo off`r`nexit /b 0"
    $failed = $false
    try { Build-LatestBackend $temporary | Out-Null } catch {
        $failed = $_.Exception.Message -like 'Missing file:*'
    }
    if (-not $failed) { throw 'Missing build output must prevent startup.' }
    if ((Get-Location).Path -ne $originalDirectory) { throw 'Build must restore the working directory.' }
    Write-Output 'PASS missing build output prevents startup and working directory is restored'
} finally {
    $resolved = (Resolve-Path -LiteralPath $temporary).Path
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing cleanup outside the temporary directory.'
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
