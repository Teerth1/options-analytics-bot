$token = Read-Host -Prompt "Enter your new Schwab Refresh Token"

if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Host "Token cannot be empty." -ForegroundColor Red
    exit
}

$jsonContent = @{
    refresh_token = $token.Trim()
} | ConvertTo-Json

$jsonContent | Set-Content -Path "schwab_tokens.json"

Write-Host "✅ Token saved to 'schwab_tokens.json'." -ForegroundColor Green
Write-Host "🚀 Now restart your bot! It will detect this file, save the token to the database, and run normally." -ForegroundColor Cyan
