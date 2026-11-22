param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$UserName = "demo-user",
    [string]$AdminName = "demo-admin"
)
$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$userPassword = $adminPassword = $userToken = $adminToken = $null

function Check([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
    Write-Host "[PASS] $Message"
}

function Read-Password([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
        $secure.Dispose()
    }
}

function Call-Api {
    param([string]$Method, [string]$Path, [hashtable]$Body = $null,
          [string]$Token = "", [hashtable]$ExtraHeaders = @{})
    $headers = @{}
    foreach ($key in $ExtraHeaders.Keys) { $headers[$key] = $ExtraHeaders[$key] }
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $options = @{
        Uri = "$BaseUrl$Path"; Method = $Method; UseBasicParsing = $true
        TimeoutSec = 25; ErrorAction = "Stop"; Headers = $headers
    }
    if ($null -ne $Body) {
        $options.ContentType = "application/json"
        $options.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Compress))
    }
    try {
        $response = Invoke-WebRequest @options
        $data = if ($response.Content) { $response.Content | ConvertFrom-Json } else { $null }
        return [pscustomobject]@{
            Status = [int]$response.StatusCode; Data = $data
            Enforcer = [string]$response.Headers['X-Auth-Enforcer']
        }
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $httpResponse = $_.Exception.Response
        $data = $null
        if ($_.ErrorDetails.Message) {
            try { $data = $_.ErrorDetails.Message | ConvertFrom-Json } catch { $data = $null }
        }
        return [pscustomobject]@{
            Status = [int]$httpResponse.StatusCode; Data = $data
            Enforcer = [string]$httpResponse.Headers['X-Auth-Enforcer']
        }
    }
}

function Check-Status($Response, [int]$Expected, [string]$Name) {
    Check ($Response.Status -eq $Expected) "$Name expected=$Expected actual=$($Response.Status)"
}

try {
    $health = Call-Api GET '/actuator/health'
    Check-Status $health 200 'GATEWAY_HEALTH'
    $anonymous = Call-Api GET '/api/textbooks'
    Check-Status $anonymous 401 'ANONYMOUS_BUSINESS'
    Check ($anonymous.Enforcer -eq 'gateway') 'ANONYMOUS_BLOCKED_BY_GATEWAY'

    $forged = @{
        'X-Authenticated-User' = $AdminName
        'X-Authenticated-Roles' = 'ADMIN'
        'X-User-Id' = '9999'; 'X-Role' = 'ADMIN'
    }
    $fakeAnonymous = Call-Api GET '/api/textbooks' $null '' $forged
    Check-Status $fakeAnonymous 401 'FAKE_HEADERS_WITHOUT_TOKEN'

    $userPassword = Read-Password "Password for $UserName"
    $adminPassword = Read-Password "Password for $AdminName"
    $userLogin = Call-Api POST '/api/auth/login' @{ username = $UserName; password = $userPassword }
    Check-Status $userLogin 200 'USER_LOGIN'
    Check ($userLogin.Data.roles -contains 'USER') 'USER_ROLE'
    $userToken = $userLogin.Data.accessToken
    Check (-not [string]::IsNullOrWhiteSpace($userToken)) 'USER_TOKEN_PRESENT'
    $adminLogin = Call-Api POST '/api/auth/login' @{ username = $AdminName; password = $adminPassword }
    Check-Status $adminLogin 200 'ADMIN_LOGIN'
    Check ($adminLogin.Data.roles -contains 'ADMIN') 'ADMIN_ROLE'
    $adminToken = $adminLogin.Data.accessToken
    Check (-not [string]::IsNullOrWhiteSpace($adminToken)) 'ADMIN_TOKEN_PRESENT'

    $allowed = Call-Api GET '/api/textbooks' $null $userToken
    Check-Status $allowed 200 'TOKEN_BUSINESS_ACCESS'
    $run = [guid]::NewGuid().ToString('N').Substring(0, 18)
    $profile = @{ studentNo = "ga-deny-$run"; displayName = 'Forbidden profile' }
    $denied = Call-Api POST '/api/users' $profile $userToken $forged
    Check-Status $denied 403 'USER_CREATE_PROFILE_WITH_FAKE_ADMIN'
    Check ($denied.Enforcer -eq 'gateway') 'ROLE_DENIED_BY_GATEWAY'
    $adminDenied = Call-Api GET '/api/auth/accounts' $null $userToken
    Check-Status $adminDenied 403 'USER_ADMIN_ACCOUNTS'
    $adminAllowed = Call-Api GET '/api/auth/accounts' $null $adminToken
    Check-Status $adminAllowed 200 'ADMIN_ACCOUNTS'

    $context = Call-Api GET '/api/auth/request-context' $null $userToken $forged
    Check-Status $context 200 'FORGED_CONTEXT_REQUEST'
    Check ($context.Data.username -eq $UserName -and $context.Data.roles -contains 'USER') 'VERIFIED_IDENTITY: JWT USER'
    Check ($context.Data.forwardedUser -eq $UserName -and $context.Data.forwardedRoles -eq 'USER') 'HEADERS_REPLACED: JWT USER'
    Check ($null -eq $context.Data.legacyUserId -and $null -eq $context.Data.legacyRole) 'LEGACY_IDENTITY_HEADERS_REMOVED'
    Write-Host "[INFO] CONTEXT username=$($context.Data.username) forwardedUser=$($context.Data.forwardedUser) forwardedRoles=$($context.Data.forwardedRoles)"

    $parts = $userToken.Split('.')
    $first = if ($parts[2][0] -eq 'A') { 'B' } else { 'A' }
    $parts[2] = $first + $parts[2].Substring(1)
    $badToken = Call-Api GET '/api/textbooks' $null ($parts -join '.')
    Check-Status $badToken 401 'TAMPERED_TOKEN'
    Check ($badToken.Enforcer -eq 'gateway') 'TAMPERED_BLOCKED_BY_GATEWAY'

    $seller = Call-Api POST '/api/users' @{ studentNo = "ga-s-$run"; displayName = 'Gateway auth seller' } $adminToken
    Check-Status $seller 201 'ADMIN_CREATE_SELLER'
    $buyer = Call-Api POST '/api/users' @{ studentNo = "ga-b-$run"; displayName = 'Gateway auth buyer' } $adminToken
    Check-Status $buyer 201 'ADMIN_CREATE_BUYER'
    $book = Call-Api POST '/api/textbooks' @{
        title = 'Gateway auth test'; isbn = "ga-$run"; author = 'Test Author'
        publisher = 'Test Publisher'; conditionDescription = 'Almost new'
        price = 25.50; stock = 3; sellerId = $seller.Data.id
    } $userToken
    Check-Status $book 201 'TOKEN_CREATE_BOOK'
    $order = Call-Api POST '/api/orders' @{
        requestId = [guid]::NewGuid().ToString(); buyerId = $buyer.Data.id
        textbookId = $book.Data.id; quantity = 1
    } $userToken
    Check-Status $order 201 'TOKEN_CREATE_ORDER'
    Check ($order.Data.status -eq 'PENDING_PAYMENT') 'ORDER_PENDING_PAYMENT'
    $query = Call-Api GET "/api/orders/$($order.Data.id)" $null $userToken
    Check-Status $query 200 'TOKEN_QUERY_ORDER'
    Check ($query.Data.id -eq $order.Data.id) 'ORDER_ID_MATCH'
    $stock = Call-Api GET "/api/textbooks/$($book.Data.id)" $null $userToken
    Check-Status $stock 200 'TOKEN_QUERY_BOOK'
    Check ($stock.Data.stock -eq 2) 'STOCK_DEDUCTED_ONCE: 3 -> 2'
    Write-Host "[INFO] FLOW orderId=$($order.Data.id) buyerId=$($buyer.Data.id) textbookId=$($book.Data.id)"

    # Inspect only published ports, never container environment or key contents.
    foreach ($service in @('user-service', 'textbook-service', 'order-service')) {
        $ids = @(docker compose ps -q $service)
        if ($LASTEXITCODE -ne 0 -or $ids.Count -eq 0) { throw "No running container: $service" }
        foreach ($id in $ids) {
            $rawPorts = docker inspect --format '{{json .NetworkSettings.Ports}}' $id
            if ($LASTEXITCODE -ne 0) { throw "Docker inspect failed: $service" }
            $portMap = $rawPorts | ConvertFrom-Json
            $bindings = @()
            if ($null -ne $portMap) {
                $bindings = @($portMap.PSObject.Properties | Where-Object { $null -ne $_.Value -and @($_.Value).Count -gt 0 })
            }
            Check ($bindings.Count -eq 0) "NO_HOST_PORT: $service $($id.Substring(0, 12))"
        }
    }
    Write-Host '[PASS] ALL GATEWAY AUTH CHECKS PASSED'
    exit 0
} catch {
    Write-Host "[FAIL] $($_.Exception.Message)"
    exit 1
} finally {
    $userPassword = $adminPassword = $userToken = $adminToken = $null
}
