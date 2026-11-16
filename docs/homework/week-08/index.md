# 第八周：服务间同步调用

### 设计与实现思路

选择“创建教材订单”作为本周的跨服务业务流程。订单服务接收客户端请求，
调用用户服务查询买家并检查启用状态，再调用教材服务查询教材、请求库存扣减，
最后保存订单。用户服务、教材服务和订单服务分别维护自己的数据库，
通过 HTTP 接口协作，避免跨服务直接访问数据库。

订单服务负责流程协调；教材服务负责在库存扣减的本地事务内，
检查教材上架状态和库存，并保存库存操作记录。
订单服务通过 requestId 识别重复订单，教材服务通过 orderNo 识别重复库存操作。

详细接口表和调用链路见
[服务间同步通信设计](../../service-communication.md)。

### 源码核对命令

在 PowerShell 中进入仓库根目录：

```powershell
Set-Location D:\microservices\microservices-practice-2412190107
```

查看下单流程：

```powershell
Get-Content services/order-service/src/main/java/com/zjgsu/zy/service/OrderService.java -Encoding utf8
```

查看调用方客户端：

```powershell
Get-Content services/order-service/src/main/java/com/zjgsu/zy/client/UserClient.java -Encoding utf8
Get-Content services/order-service/src/main/java/com/zjgsu/zy/client/TextbookClient.java -Encoding utf8
```

核对各服务接口路径：

```powershell
Get-ChildItem .\services -Recurse -Filter '*Controller.java' |
    Select-String -Pattern '@RequestMapping|@GetMapping|@PostMapping|@PutMapping'
```

### 核对结果

通过源码核对确认：

- 创建订单入口为 `POST /api/orders`。
- 订单服务调用用户服务的 `GET /api/users/{id}` 查询买家。
- 订单服务调用教材服务的 `GET /api/textbooks/{id}` 查询教材。
- 库存扣减接口为
  `PUT /internal/textbooks/{id}/stock-deductions/{orderNo}`。
- 远程校验及库存请求成功后，订单服务才保存本地订单。
- 当前调用方式为 RestTemplate；教材服务实例通过 Nacos 服务发现获取。
- 本步骤只完成流程选择与文档设计，未重新运行服务或执行业务测试。

### 已有验证记录

根据此前项目验证，订单创建、订单查询及库存变化已验证通过。
教材服务无可用实例时，下单返回 503；
教材服务恢复后重新发送下单请求，可以成功创建订单。
以上为此前验证结果，本周后续测试需另行记录。

### 关键结论

创建教材订单具有明确的跨服务协作需求，三个服务的数据边界和职责清晰。
后续将在该流程中引入 OpenFeign，并继续完成负载均衡验证和测试。
现有请求编号及库存操作记录可以减少重复订单和重复扣减，
但库存操作与订单保存仍属于不同的本地事务。

## 使用 OpenFeign

### 实现思路

在订单服务中引入 spring-cloud-starter-openfeign 和
spring-cloud-starter-loadbalancer，启动类通过 @EnableFeignClients
启用 UserFeignClient。

调用方为 services/order-service/，被调用方为 services/user-service/。
客户端通过 @FeignClient(name = "user-service")
调用 GET /api/users/{id}，正式环境不配置固定 URL，
通过 Nacos 服务发现获取用户服务实例。

下单时，OrderService 调用 UserClient.requireEnabled()，
由 UserClient 发起 Feign 请求并检查用户启用状态。
连接超时为 2000ms，读取超时为 3000ms。

Feign 调用失败时转换为 ApiException，再由统一异常处理器返回
包含 code、message 的 JSON。用户不存在返回 422；
用户服务不可用、连接失败或超时返回 503，
错误信息提示保留原 requestId 重试。

### 构建与运行命令

在仓库根目录执行，构建成功后再重新创建订单服务：

```powershell
mvn -f services/order-service/pom.xml clean verify
docker compose --progress plain build order-service
docker compose up -d --no-deps --force-recreate order-service
docker compose ps order-service
```

验证时使用已启用的买家 2、已上架且库存充足的教材 4。
请求文件 work/w08-order.json 包含 requestId、buyerId、
textbookId 和 quantity。每个独立场景使用新的 UUID；
同一次失败后的恢复重试保留原 UUID。

发送下单请求：

```powershell
curl.exe -i -X POST "http://localhost:8083/api/orders" `
    -H "Content-Type: application/json" `
    --data-binary "@work/w08-order.json"
```

查看 Feign 调用日志：

```powershell
docker compose logs --since=5m order-service |
    Select-String -Pattern "UserFeignClient" -Context 1,2
```

停止用户服务，并为失败场景生成新的请求编号：

```powershell
docker compose stop user-service

$orderRequest = Get-Content .\work\w08-order.json -Raw | ConvertFrom-Json
$orderRequest.requestId = [guid]::NewGuid().ToString()

$orderRequest | ConvertTo-Json |
    Set-Content .\work\w08-order.json -Encoding ascii
```

再次发送上述下单请求，记录失败响应。

恢复用户服务：

```powershell
docker compose up -d --no-deps user-service
docker compose ps user-service
```

等待服务健康且重新注册后，使用原请求文件重新发送下单请求，
不更换 requestId。

### 实际验证结果

| 验证项目 | 实际结果 |
|---|---|
| 容器中的新版代码 | JAR 包含 UserFeignClient.class 和 spring-cloud-openfeign-core-5.0.0.jar |
| 正常同步调用 | Feign 日志显示 GET http://user-service/api/users/2，返回 HTTP 200，耗时 159ms |
| 用户服务停止 | 下单返回 HTTP 503，code 为 DEPENDENCY_UNAVAILABLE |
| 清晰错误信息 | 用户服务暂时不可用，请稍后使用原requestId重试 |
| 用户服务恢复后重试 | 返回 HTTP 201，订单 ID 为 11，状态为 PENDING_PAYMENT |

## 四、负载均衡验证

### 4.1 实现思路

将被调用方 user-service 启动为两个实例，
两个实例使用相同服务名注册到 Nacos 的 CAMPUS_TEXTBOOK_GROUP。

订单服务继续通过 OpenFeign 按 user-service 服务名调用。
通过现有 Spring Cloud LoadBalancer 从服务发现提供的实例中选择目标。

为证明请求确实到达两个实例，
客户端向订单服务传入唯一的 X-LB-Test-Id 请求头，
订单服务通过 Feign RequestInterceptor 转发该标记。
用户服务成功查询用户后，记录请求标记、用户编号和容器主机名。

验证请求统一发送到订单服务的 POST /api/orders，
没有通过分别直连两个用户实例来制造分发结果。

### 4.2 Compose 多实例配置

在 user-service 中设置两个实例，并使用宿主机端口范围：

```yaml
  user-service:
    <<: *app-common
    scale: 2
    image: campus-user-service:week06

    # 保留原有 build、depends_on、environment 等配置

    ports:
      - target: 8080
        published: "18091-18099"
        host_ip: 127.0.0.1
        protocol: tcp
```

两个实例的容器内部端口均为 8080，
但绑定不同的宿主机端口。

本次启动截图中的映射为：

| 容器 | 宿主机地址 | 容器端口 |
|---|---|---:|
| user-service-1 | 127.0.0.1:18091 | 8080 |
| user-service-2 | 127.0.0.1:18092 | 8080 |

端口范围内的实际分配和容器主机名可能在重建后变化，
复现时以当前查询结果为准。

### 4.3 请求标记与实例日志

订单服务的 HttpClientConfig 增加 Feign RequestInterceptor，
读取当前 HTTP 请求中的 X-LB-Test-Id 并转发到用户服务。

用户服务的 UserController 在查询成功后记录：

```text
USER_QUERY instance=<容器主机名> userId=<用户编号> lbTestId=<请求标记>
```

该请求头为可选参数，不改变原有用户 JSON 响应结构。

对应代码位置：

- `services/order-service/src/main/java/com/zjgsu/zy/config/HttpClientConfig.java`
- `services/user-service/src/main/java/com/zjgsu/zy/controller/UserController.java`

### 4.4 构建和启动命令

```powershell
docker compose config --quiet

docker compose --progress plain build user-service order-service

docker compose up -d --no-deps --force-recreate --scale user-service=2 user-service

docker compose ps -a user-service
```

启动瞬间可能显示 health: starting。
执行验证前等待两个实例健康，并确认 Nacos 中存在两个健康实例。

查询端口及主机名：

```powershell
docker compose port --index 1 user-service 8080
docker compose port --index 2 user-service 8080

docker compose ps -q user-service | ForEach-Object {
    docker inspect --format '{{.Name}} hostname={{.Config.Hostname}}' $_
}
```

重新创建订单服务，使其加载最新代码并获取当前实例列表：

```powershell
docker compose up -d --no-deps --force-recreate order-service
docker compose ps order-service
```

### 4.5 准备独立测试教材

使用已有的启用卖家 1、买家 2。
新发布库存为 20 的教材，避免之前验证消耗的库存影响本次结果：

```powershell
$bookBody = @{
    title = "LoadBalancer Test Book"
    isbn = "lb-" + [guid]::NewGuid().ToString("N").Substring(0, 20)
    author = "Test Author"
    publisher = "Test Publisher"
    conditionDescription = "Almost new"
    price = 25.50
    stock = 20
    sellerId = 1
} | ConvertTo-Json

$lbBook = Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8082/api/textbooks" `
    -ContentType "application/json" `
    -Body $bookBody

$lbBook
```

### 4.6 通过订单服务连续发送六次请求

以下命令在同一个 PowerShell 窗口中执行。
每次使用新的 requestId，确保实际触发用户服务查询。

```powershell
$runId = "lb-" + [guid]::NewGuid().ToString("N")
Write-Host "本批验证标记：$runId"

$results = 1..6 | ForEach-Object {
    $testId = "$runId-$_"

    $body = @{
        requestId = [guid]::NewGuid().ToString()
        buyerId = 2
        textbookId = $lbBook.id
        quantity = 1
    } | ConvertTo-Json

    $response = Invoke-WebRequest `
        -UseBasicParsing `
        -Method Post `
        -Uri "http://localhost:8083/api/orders" `
        -ContentType "application/json" `
        -Headers @{ "X-LB-Test-Id" = $testId } `
        -Body $body

    $order = $response.Content | ConvertFrom-Json

    [pscustomobject]@{
        TestId = $testId
        HttpStatus = [int]$response.StatusCode
        OrderId = $order.id
        Status = $order.status
    }
}

$results | Format-Table -AutoSize
```

### 4.7 查询日志并统计实例命中次数

立即在同一 PowerShell 窗口执行：

```powershell
New-Item -ItemType Directory -Force .\work | Out-Null

$lbLogs = docker compose logs --no-color --since=10m user-service |
    Select-String -SimpleMatch $runId |
    ForEach-Object { $_.Line }

$lbLogs | Tee-Object -FilePath .\work\w08-lb-user.log

$hits = $lbLogs | ForEach-Object {
    if ($_ -match 'instance=(\S+)') {
        $Matches[1]
    }
} | Group-Object

$hits | Select-Object @{Name="Instance"; Expression={$_.Name}}, Count
```

日志通过本批次唯一前缀过滤，
避免将启动日志、历史调用或教材发布时的用户查询计入结果。

### 4.8 实际验证结果

验证日期：2026 年 11 月 16 日。

本批次标记：

```text
lb-9f4e0c9326604db4ac1dc967f982d915
```

六次下单结果：

| 请求序号 | HTTP 状态 | 订单 ID | 订单状态 |
|---|---:|---:|---|
| 1 | 201 | 12 | PENDING_PAYMENT |
| 2 | 201 | 13 | PENDING_PAYMENT |
| 3 | 201 | 14 | PENDING_PAYMENT |
| 4 | 201 | 15 | PENDING_PAYMENT |
| 5 | 201 | 16 | PENDING_PAYMENT |
| 6 | 201 | 17 | PENDING_PAYMENT |

用户服务日志中的实际命中情况：

| 实例 | 容器主机名 | 请求序号 | 命中次数 |
|---|---|---|---:|
| user-service-1 | 2cc0e594b082 | 2、4、6 | 3 |
| user-service-2 | e7a2c78ac107 | 1、3、5 | 3 |

日志中的 lbTestId 与下单请求表中的 TestId 对应，
两个不同实例均实际处理了本批次订单服务发出的用户查询。

本次六个请求表现为交替命中两个实例。
这证明本批次分发成功，不代表所有请求规模下都保证严格等量分配。

### 4.9 关键结论

两个用户服务实例通过不同宿主机端口运行，
订单服务使用同一个 user-service 服务名进行调用。
通过唯一请求标记、不同容器日志前缀和不同主机名，
可以核验调用方实际访问到了两个实例。

本次六次下单全部成功，两个实例各处理三次用户查询，
满足负载均衡验证要求，证据覆盖了实际请求分发。
