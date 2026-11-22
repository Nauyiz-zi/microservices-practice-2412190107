param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$UserName = "demo-user",
    [string]$AdminName = "demo-admin"
)
$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$userPassword = $null
$adminPassword = $null
$userToken = $null
$adminToken = $null

function Assert-Auth([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
    Write-Host "[PASS] $Message"
}

function Read-PlainPassword([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
        $secure.Dispose()
    }
}

function Call-AuthApi {
    param([string]$Method, [string]$Path,
          [hashtable]$Body = $null, [string]$Token = "")
    $argsForRequest = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        UseBasicParsing = $true
        TimeoutSec = 20
        ErrorAction = "Stop"
    }
    if ($null -ne $Body) {
        $argsForRequest.ContentType = "application/json"
        $argsForRequest.Body = [Text.Encoding]::UTF8.GetBytes(
            ($Body | ConvertTo-Json -Compress))
    }
    if ($Token) {
        $argsForRequest.Headers = @{ Authorization = "Bearer $Token" }
    }
    try {
        $response = Invoke-WebRequest @argsForRequest
        $parsed = if ($response.Content) {
            $response.Content | ConvertFrom-Json
        } else { $null }
        return [pscustomobject]@{
            StatusCode = [int]$response.StatusCode
            Body = $parsed
        }
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $statusCode = [int]$_.Exception.Response.StatusCode
        $parsed = $null
        if ($_.ErrorDetails.Message) {
            try { $parsed = $_.ErrorDetails.Message | ConvertFrom-Json }
            catch { $parsed = $null }
        }
        return [pscustomobject]@{ StatusCode = $statusCode; Body = $parsed }
    }
}

try {
    $health = Call-AuthApi GET "/actuator/health"
    Assert-Auth ($health.StatusCode -eq 200) "GATEWAY_HEALTH: HTTP=200"

    $userPassword = Read-PlainPassword "Password for $UserName"
    $adminPassword = Read-PlainPassword "Password for $AdminName"

    $userLogin = Call-AuthApi POST "/api/auth/login" @{
        username = $UserName
        password = $userPassword
    }
    Assert-Auth ($userLogin.StatusCode -eq 200) "USER_LOGIN: expected=200 actual=$($userLogin.StatusCode) code=$($userLogin.Body.code) message=$($userLogin.Body.message)"
    Assert-Auth ($userLogin.Body.roles -contains "USER") "USER_ROLE: USER"
    Assert-Auth ($userLogin.Body.tokenType -eq "Bearer" -and $userLogin.Body.expiresIn -eq 900) "TOKEN_TYPE_AND_TTL: Bearer, 900 seconds"
    $userToken = $userLogin.Body.accessToken
    Assert-Auth ($userToken -and $userToken.Split(".").Count -eq 3) "JWT_FORMAT: 3 parts"

    $adminLogin = Call-AuthApi POST "/api/auth/login" @{
        username = $AdminName
        password = $adminPassword
    }
    Assert-Auth ($adminLogin.StatusCode -eq 200) "ADMIN_LOGIN: HTTP=200"
    Assert-Auth ($adminLogin.Body.roles -contains "ADMIN") "ADMIN_ROLE: ADMIN"
    $adminToken = $adminLogin.Body.accessToken

    $wrong = Call-AuthApi POST "/api/auth/login" @{
        username = $UserName
        password = [guid]::NewGuid().ToString()
    }
    Assert-Auth ($wrong.StatusCode -eq 401) "WRONG_PASSWORD: HTTP=401"

    $missing = Call-AuthApi GET "/api/auth/me"
    Assert-Auth ($missing.StatusCode -eq 401) "MISSING_TOKEN: HTTP=401"

    1..5 | ForEach-Object {
        $me = Call-AuthApi GET "/api/auth/me" $null $userToken
        Assert-Auth ($me.StatusCode -eq 200 -and $me.Body.username -eq $UserName) "VERIFIED_ME_${_}: HTTP=200"
    }

    $denied = Call-AuthApi GET "/api/auth/accounts" $null $userToken
    Assert-Auth ($denied.StatusCode -eq 403) "USER_ADMIN_ENDPOINT: HTTP=403"

    $allowed = Call-AuthApi GET "/api/auth/accounts" $null $adminToken
    Assert-Auth ($allowed.StatusCode -eq 200) "ADMIN_ACCOUNTS: HTTP=200"
    Assert-Auth (@($allowed.Body.username) -contains $UserName) "ADMIN_ACCOUNT_LIST: user present"
    Assert-Auth (-not (@($allowed.Body)[0].PSObject.Properties.Name -contains "passwordHash")) "ACCOUNT_RESPONSE: no password hash"

    $parts = $userToken.Split(".")
    $first = if ($parts[2][0] -eq "A") { "B" } else { "A" }
    $parts[2] = $first + $parts[2].Substring(1)
    $tampered = Call-AuthApi GET "/api/auth/me" $null ($parts -join ".")
    Assert-Auth ($tampered.StatusCode -eq 401) "TAMPERED_TOKEN: HTTP=401"

    $jwks = Call-AuthApi GET "/.well-known/jwks.json"
    Assert-Auth ($jwks.StatusCode -eq 200 -and $jwks.Body.keys[0].kty -eq "RSA") "PUBLIC_JWKS: HTTP=200 RSA"
    Assert-Auth (-not ($jwks.Body.keys[0].PSObject.Properties.Name -contains "d")) "PUBLIC_JWKS: no private key"

    Write-Host "[PASS] ALL AUTH CHECKS PASSED"
    exit 0
} catch {
    Write-Host "[FAIL] $($_.Exception.Message)"
    exit 1
} finally {
    $userPassword = $null
    $adminPassword = $null
    $userToken = $null
    $adminToken = $null
}
