param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$resolvedPath = Resolve-Path -LiteralPath $Path -ErrorAction Stop

foreach ($line in Get-Content -LiteralPath $resolvedPath -Encoding UTF8) {
    $trimmed = $line.Trim()

    if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
        continue
    }

    $separator = $trimmed.IndexOf('=')
    if ($separator -le 0) {
        throw "Invalid .env line: $line"
    }

    $name = $trimmed.Substring(0, $separator).Trim()
    $value = $trimmed.Substring($separator + 1).Trim()

    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
        ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

