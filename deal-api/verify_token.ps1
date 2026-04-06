$clientId = $env:SCHWAB_CLIENT_ID
$clientSecret = $env:SCHWAB_CLIENT_SECRET
$refreshToken = $env:SCHWAB_REFRESH_TOKEN

if (-not $clientId -or -not $clientSecret -or -not $refreshToken) {
    Write-Host "Error: Missing one or more environment variables (SCHWAB_CLIENT_ID, SCHWAB_CLIENT_SECRET, SCHWAB_REFRESH_TOKEN)" -ForegroundColor Red
    Write-Host "Please set them in your terminal before running this script."
    exit 1
}

Write-Host "Testing Schwab Authentication..."
Write-Host "Client ID: $clientId"
if ($refreshToken.Length -gt 10) {
    Write-Host "Refresh Token: $($refreshToken.Substring(0, 10))..."
} else {
    Write-Host "Refresh Token: (Too short to display)"
}

$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${clientId}:${clientSecret}"))
$body = "grant_type=refresh_token&refresh_token=$refreshToken"

try {
    $response = Invoke-RestMethod -Uri 'https://api.schwabapi.com/v1/oauth/token' -Method Post -Headers @{Authorization="Basic $auth"; "Content-Type"="application/x-www-form-urlencoded"} -Body $body -ErrorAction Stop
    Write-Host "Success! Access Token received." -ForegroundColor Green
    Write-Host "Token expires in: $($response.expires_in) seconds"
} catch {
    Write-Host "Authentication Failed!" -ForegroundColor Red
    Write-Host $_.Exception.Message
    if ($_.Exception.Response) {
        # Try to read the error stream
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            if ($stream) {
                $reader = New-Object System.IO.StreamReader($stream)
                Write-Host "Response Body: $($reader.ReadToEnd())" -ForegroundColor Yellow
            }
        } catch {
            Write-Host "Could not read error response body."
        }
    }
}
