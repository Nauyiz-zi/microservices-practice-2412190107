# 第九周：API Gateway 与 JWT 认证

本周目标是为校园二手教材微服务系统增加统一 API 入口、
JWT 认证与受保护接口访问。

当前已完成第一项 API Gateway：
新增独立网关工程，通过服务名转发用户、教材和订单请求，
并完成健康检查及实际业务流程验证。
JWT 登录、验签和角色鉴权将在后续步骤实现。

## 一、API Gateway

### 1.1 实现目标与设计

在仓库根目录 services/gateway-service/ 新建独立工程，
使用 Spring Cloud Gateway Server WebFlux 提供统一入口。

网关对外端口为 8080。
客户端通过同一个网关地址访问用户、教材和订单接口，
网关根据请求路径匹配路由，
通过 Nacos 服务发现及 Spring Cloud LoadBalancer
访问对应业务服务实例。

网关只承担入口和路由转发职责，
业务数据仍由各业务服务及其数据库管理。
本步骤没有修改原有下单业务规则。

### 1.2 工程结构

```text
services/gateway-service/
├── pom.xml
├── Dockerfile
├── .dockerignore
└── src/main/
    ├── java/com/zjgsu/zy/gateway/GatewayApplication.java
    └── resources/application.yml
```

主要依赖：

| 依赖 | 用途 |
|---|---|
| spring-cloud-starter-gateway-server-webflux | 网关路由和请求转发 |
| spring-cloud-starter-loadbalancer | 按服务名选择实例 |
| spring-cloud-starter-alibaba-nacos-discovery | Nacos 服务注册与发现 |
| spring-boot-starter-actuator | 网关健康检查 |

版本与现有业务服务保持一致：

| 项目 | 版本 |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.8 |
| Spring Cloud | 2025.1.0 |
| Spring Cloud Alibaba | 2025.1.0.0 |

### 1.3 统一入口与路由

统一业务入口：

```text
http://localhost:8080
```

配置三条路由：

| 路由 ID | 匹配路径 | 路由目标 |
|---|---|---|
| user-route | /api/users、/api/users/** | lb://user-service |
| textbook-route | /api/textbooks、/api/textbooks/** | lb://textbook-service |
| order-route | /api/orders、/api/orders/** | lb://order-service |

路由目标使用服务名，不写死容器 IP。

网关使用 Nacos 分组 CAMPUS_TEXTBOOK_GROUP，
与三个业务服务保持一致。

请求路径原样转发。例如：

```text
GET http://localhost:8080/api/users/10
    → 网关匹配 user-route
    → 发现 user-service 实例
    → GET /api/users/10
```

网关路由配置位于：

```text
services/gateway-service/src/main/resources/application.yml
```

关键配置：

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          httpclient:
            connect-timeout: 2000
            response-timeout: 20s

          default-filters:
            - SetResponseHeader=X-Gateway-Service,gateway-service

          routes:
            - id: user-route
              uri: lb://user-service
              predicates:
                - Path=/api/users,/api/users/**
              filters:
                - SetResponseHeader=X-Gateway-Route,user-route

            - id: textbook-route
              uri: lb://textbook-service
              predicates:
                - Path=/api/textbooks,/api/textbooks/**
              filters:
                - SetResponseHeader=X-Gateway-Route,textbook-route

            - id: order-route
              uri: lb://order-service
              predicates:
                - Path=/api/orders,/api/orders/**
              filters:
                - SetResponseHeader=X-Gateway-Route,order-route
```

每条业务路由都会返回：

```text
X-Gateway-Service: gateway-service
```

并通过 X-Gateway-Route 标识具体匹配的路由，
结合 HTTP 状态和业务 JSON 核验实际转发结果。

### 1.4 健康检查入口

保留网关自身的健康检查接口：

```text
GET /actuator/health
```

该接口由网关自身的 Actuator 提供，
不转发到用户、教材或订单服务。

配置如下：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: never
```

### 1.5 Docker Compose 编排

在根目录 compose.yaml 中增加 gateway-service：

```yaml
  gateway-service:
    <<: *app-common
    image: campus-gateway-service:week09
    build:
      context: ./services/gateway-service
      dockerfile: Dockerfile

    depends_on:
      nacos:
        condition: service_started
      user-service:
        condition: service_healthy
      textbook-service:
        condition: service_healthy
      order-service:
        condition: service_healthy

    environment:
      SERVER_PORT: "8080"
      NACOS_SERVER_ADDR: nacos:8848
      NACOS_USERNAME: ${NACOS_USERNAME:?Set NACOS_USERNAME in .env}
      NACOS_PASSWORD: ${NACOS_PASSWORD:?Set NACOS_PASSWORD in .env}
      NACOS_GROUP: CAMPUS_TEXTBOOK_GROUP
      TZ: Asia/Shanghai

    ports:
      - "127.0.0.1:8080:8080"
```

网关加入现有 campus_net 网络，
复用 app-common 的健康检查和重启配置。

Nacos 凭据通过环境变量读取，
没有在代码或文档中填写真实密码。

现有业务服务直连端口保留用于本地排查，
本次业务验证全部通过网关 8080 入口完成。

### 1.6 构建与运行命令

在 PowerShell 中进入仓库根目录：

```powershell
Set-Location D:\microservices\microservices-practice-2412190107
```

检查 Compose 配置并构建网关：

```powershell
docker compose config --quiet

docker compose --progress plain build gateway-service
```

构建成功后启动运行环境：

```powershell
docker compose up -d nacos

docker compose up -d user-service textbook-service order-service

docker compose up -d --no-deps --force-recreate gateway-service

docker compose ps gateway-service
```

查看网关日志：

```powershell
docker compose logs --tail=150 gateway-service
```

检查健康状态：

```powershell
curl.exe -i "http://localhost:8080/actuator/health"
```

### 1.7 端口冲突处理记录

首次启动网关时，宿主机 8080 被第五周的旧单体应用占用，
Docker 返回：

```text
Bind for 127.0.0.1:8080 failed: port is already allocated
```

通过以下命令定位：

```powershell
docker ps --format 'table {{.Names}}\t{{.Ports}}'
```

发现占用容器为：

```text
campus-textbook-week05-app-1
127.0.0.1:8080->8080/tcp
```

停止旧单体应用并重新启动网关：

```powershell
docker stop campus-textbook-week05-app-1

docker compose up -d --no-deps gateway-service
```

处理后，网关成功绑定宿主机 8080。
该停止操作仅针对本次确认占用端口的旧单体应用。

### 1.8 通过网关准备测试数据

以下命令在同一个 PowerShell 窗口中执行。
所有业务请求均使用 http://localhost:8080。

创建独立卖家和买家：

```powershell
$gwTag = [guid]::NewGuid().ToString("N").Substring(0, 16)

$sellerBody = @{
    studentNo = "gw-s-$gwTag"
    displayName = "Gateway Seller"
} | ConvertTo-Json

$gwSeller = Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/users" `
    -ContentType "application/json" `
    -Body $sellerBody

$buyerBody = @{
    studentNo = "gw-b-$gwTag"
    displayName = "Gateway Buyer"
} | ConvertTo-Json

$gwBuyer = Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/users" `
    -ContentType "application/json" `
    -Body $buyerBody
```

发布初始库存为 3 的测试教材：

```powershell
$bookBody = @{
    title = "Gateway Test Book"
    isbn = "gw-$gwTag"
    author = "Test Author"
    publisher = "Test Publisher"
    conditionDescription = "Almost new"
    price = 25.50
    stock = 3
    sellerId = $gwSeller.id
} | ConvertTo-Json

$gwBook = Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/textbooks" `
    -ContentType "application/json" `
    -Body $bookBody
```

### 1.9 通过网关创建订单

使用本次创建的买家和教材，购买数量为 1：

```powershell
$orderBody = @{
    requestId = [guid]::NewGuid().ToString()
    buyerId = $gwBuyer.id
    textbookId = $gwBook.id
    quantity = 1
} | ConvertTo-Json

$gwOrderResponse = Invoke-WebRequest `
    -UseBasicParsing `
    -Method Post `
    -Uri "http://localhost:8080/api/orders" `
    -ContentType "application/json" `
    -Body $orderBody

$gwOrder = $gwOrderResponse.Content | ConvertFrom-Json

$gwOrderResponse.StatusCode
$gwOrder
```

### 1.10 查询三条路由

查询用户：

```powershell
curl.exe -i "http://localhost:8080/api/users/$($gwBuyer.id)"
```

查询教材：

```powershell
curl.exe -i "http://localhost:8080/api/textbooks/$($gwBook.id)"
```

查询订单：

```powershell
curl.exe -i "http://localhost:8080/api/orders/$($gwOrder.id)"
```

每次检查 HTTP 状态、路由响应头和业务 JSON，
确认实际访问了对应业务服务。

### 1.11 实际验证结果

验证日期：2026 年 11 月 19 日。

#### 网关运行与健康检查

容器：

```text
campus-textbook-week06-gateway-service-1
```

镜像：

```text
campus-gateway-service:week09
```

实际状态及端口：

```text
Up (healthy)
127.0.0.1:8080->8080/tcp
```

健康检查返回：

```text
HTTP/1.1 200 OK
```

响应体：

```json
{
  "groups": [
    "liveness",
    "readiness"
  ],
  "status": "UP"
}
```

#### 三条路由验证

| 路由 | 实际请求路径 | HTTP 状态 | X-Gateway-Route | 业务结果 |
|---|---|---:|---|---|
| 用户路由 | /api/users/10 | 200 | user-route | 返回买家资料，enabled 为 true |
| 教材路由 | /api/textbooks/7 | 200 | textbook-route | 返回教材资料，stock 为 2 |
| 订单路由 | /api/orders/20 | 200 | order-route | 返回订单资料，状态为 PENDING_PAYMENT |

三条响应均包含：

```text
X-Gateway-Service: gateway-service
```

用户查询返回的关键数据：

```json
{
  "id": 10,
  "studentNo": "gw-b-7792cd51fd7c4eed",
  "displayName": "Gateway Buyer",
  "enabled": true
}
```

教材查询返回的关键数据：

```json
{
  "id": 7,
  "title": "Gateway Test Book",
  "isbn": "gw-7792cd51fd7c4eed",
  "price": 25.50,
  "stock": 2,
  "sellerId": 9,
  "listed": true
}
```

#### 网关下单结果

创建订单实际返回：

```text
HTTP 201
```

关键业务结果：

| 字段 | 实际值 |
|---|---|
| 订单 ID | 20 |
| 买家 ID | 10 |
| 卖家 ID | 9 |
| 教材 ID | 7 |
| 教材名称 | Gateway Test Book |
| 购买数量 | 1 |
| 单价 | 25.50 |
| 总金额 | 25.50 |
| 订单状态 | PENDING_PAYMENT |

本次请求编号：

```text
b68e3a3e-06e7-4547-8bc3-18dc7ee7525d
```

本次订单编号：

```text
ORDb68e3a3e06e745478bc318dc7ee7525d
```

测试教材初始化库存为 3，购买一本后查询库存为 2，
与本次订单数量一致。
随后通过网关查询订单 20，返回 HTTP 200 和对应订单 JSON。

### 1.12 完成情况与关键结论

已新增独立 gateway-service 工程，并在根目录 compose.yaml
配置网关构建、启动、端口和健康检查。

网关通过 8080 提供统一入口，
配置用户、教材和订单三条基于服务名的路由，
保留 /actuator/health 健康检查入口。

实际验证中，网关健康检查返回 200 和 UP，
三个业务查询均返回 200、对应路由响应头和业务 JSON。
通过网关创建订单返回 201，
订单查询和教材库存变化与下单结果一致。

以上结果证明统一入口和三条路由转发均已生效。
本步骤完成 API Gateway，
JWT 登录、验签、角色规则及受保护接口访问尚待后续步骤实现。

本次卖家和买家为业务用户资料。
登录测试账号及角色将在 JWT 认证实现后补充到 README。

复现时应使用新建数据返回的编号，
不应假定用户、教材和订单 ID 始终与本次相同。

## 二、认证服务

### 2.1 设计

认证功能位于 services/user-service/。新增 auth_accounts 表，
与现有 users 业务资料表分离，保留原业务用户接口和服务协作。
认证账号包括 USER、ADMIN 两种角色；角色由服务端初始化写入，
登录请求只接收用户名和密码，不允许客户端自行指定角色。

密码使用 BCrypt cost=12 和每次编码生成的随机盐。
初始化只接收环境变量中的BCrypt哈希，不接收明文初始化密码，
不覆盖已有密码、角色或禁用状态。唯一约束及冲突后的重新读取，
保证两个用户实例同时启动时不会创建重复账号。

### 2.2 接口与安全规则

| 接口 | 规则 |
|---|---|
| POST /api/auth/login | 匿名可访问；错误密码或禁用账号返回401 |
| GET /api/auth/me | 必须携带有效Bearer JWT |
| GET /api/auth/accounts | 仅ADMIN可访问，响应不包含密码哈希 |
| GET /.well-known/jwks.json | 公开验签公钥，不包含私钥 |

JWT采用RS256签发，包含sub、roles、iss、aud、iat、nbf、exp、jti，
有效期900秒。验签固定使用RS256，并检查有效期、issuer与audience。
未认证返回401，角色不足返回403，响应包含清晰的JSON错误。

两个用户服务实例共享同一RSA密钥对。密钥在本地生成，
通过只读挂载提供给用户服务，不写入代码、镜像或Git仓库。
网关新增auth-route，通过lb://user-service转发认证及公钥请求。

本步骤验证认证域内的验签和角色规则。
旧业务接口暂按上一阶段规则运行，全站业务入口鉴权在下一步收紧。

### 2.3 主要代码

- services/user-service/src/main/java/com/zjgsu/zy/auth/
- services/user-service/src/test/java/com/zjgsu/zy/auth/AuthApiTest.java
- services/user-service/src/test/java/com/zjgsu/zy/auth/TestJwtKeys.java
- services/user-service/src/test/java/com/zjgsu/zy/auth/PasswordHashTool.java
- scripts/auth-smoke.ps1
- 用户服务和网关的application.yml，以及根目录compose.yaml

### 2.4 测试账号

| 账号 | 角色 | 密码 |
|---|---|---|
| demo-user | USER | 本机交互设置，文档不保存明文 |
| demo-admin | ADMIN | 本机交互设置，文档不保存明文 |

若修改初始化账号名，应同步更新README及脚本参数。

### 2.5 运行命令

在仓库根目录PowerShell执行：

```powershell
mvn -f services/user-service/pom.xml clean verify
docker compose config --quiet
docker compose --progress plain build user-service gateway-service
docker compose up -d --no-deps --force-recreate --scale user-service=1 user-service
# 等第一个实例healthy，完成新表和账号初始化后，再扩到两个实例：
docker compose up -d --no-deps --scale user-service=2 user-service
docker compose ps user-service
docker compose up -d --no-deps --force-recreate gateway-service
docker compose ps gateway-service
& .\scripts\auth-smoke.ps1
```

运行前先完成本地密钥生成、BCrypt哈希生成及.env配置。
脚本通过交互读取密码，不在命令行、脚本文件或日志中保存明文。
成功输出ALL AUTH CHECKS PASSED并退出0；失败输出FAIL并退出1。

### 2.6 验证方法

自动化测试使用H2及运行时生成的临时RSA密钥，
验证登录、哈希存储、错误密码、缺失Token、角色差异、
签名篡改、过期、错误issuer/audience及公钥不泄露私钥。

真实环境脚本通过网关验证两种角色登录、
普通用户访问me成功但访问账号列表返回403、
管理员访问账号列表成功、错误密码和缺失/篡改Token返回401。

数据库查询核对auth_accounts只有password_hash，
哈希前缀为BCrypt cost=12，长度为60；业务响应不返回该字段。

### 2.7 实际验证结果

| 验证项目 | 实际结果 |
|---|---|
| USER、ADMIN 登录 | 均返回 HTTP 200 和 JWT |
| Token 类型及有效期 | Bearer，900 秒 |
| 有效 JWT 查询当前身份 | 连续五次返回 HTTP 200 |
| 错误密码 | HTTP 401 |
| 缺少 Token | HTTP 401 |
| 篡改 Token 签名 | HTTP 401 |
| USER 访问管理员接口 | HTTP 403 |
| ADMIN 访问账号列表 | HTTP 200 |
| 账号列表响应 | 不包含密码哈希 |
| 公开公钥接口 | HTTP 200，不包含私钥 |
| 数据库密码存储 | 两个账号均为 BCrypt cost=12，哈希长度60 |
| 认证脚本 | ALL AUTH CHECKS PASSED |

认证流程与数据库查询共同验证了登录、JWT签发验签、
角色差异及密码哈希存储。认证账号使用USER和ADMIN两种角色，
数据库保存BCrypt哈希，未保存明文登录密码。

##三、 网关鉴权

### 3.1 实现思路

网关使用 Spring Security Reactive Resource Server 校验 RS256 JWT，验证签名、有效期、issuer 和 audience。公开健康检查、登录及 JWKS；其他业务 API 要求 USER 或 ADMIN 角色。创建用户资料与查询认证账号列表要求 ADMIN。没有有效 Token 返回 401，已登录但权限不足返回 403。

网关在转发前删除客户端提供的身份和角色头，再根据验签后的 JWT 重建 `X-Authenticated-User`、`X-Authenticated-Roles`。下游 `/api/auth/request-context` 返回再次验签的身份和实际收到的头，用于核验伪造头不能冒充管理员。

Compose 取消三个业务服务的宿主机端口映射，仅网关发布业务入口 8080。下游仍可通过受信任的 Docker 内网完成调用；拥有 Docker 管理权限或内网容器访问能力不属于该网络隔离的防御范围。完整路由表和安全边界见 `docs/security.md`。

### 3.2 运行命令

在仓库根目录 PowerShell 中执行，保留之前配置的本地密钥和环境变量：

```powershell
docker compose config --quiet
mvn -f .\services\gateway-service\pom.xml test
mvn -f .\services\user-service\pom.xml test

docker compose build user-service gateway-service
docker compose up -d --no-deps --force-recreate --scale user-service=2 user-service
# 待两个用户服务实例均 healthy 后继续。
docker compose up -d --no-deps --force-recreate textbook-service order-service
# 待教材、订单服务 healthy 后继续。
docker compose up -d --no-deps --force-recreate gateway-service
docker compose ps

powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\gateway-auth-smoke.ps1
$LASTEXITCODE
```

脚本交互输入 demo-user、demo-admin 的本地密码，不输出密码或完整 Token。运行中创建唯一编号的测试资料、教材和订单。全部通过返回 0；失败返回 1。

### 3.3 验证结果

实际 Docker 环境验证通过。网关、两个用户服务实例、教材服务和订单服务均为 healthy；业务宿主机端口仅由网关发布为 `127.0.0.1:8080`，三个下游服务仅保留容器内部 `8080/tcp`。Nacos 的独立管理端口保留。

执行 `gateway-auth-smoke.ps1`，所有断言通过，最后输出 `ALL GATEWAY AUTH CHECKS PASSED`，随后查询 `$LASTEXITCODE` 为 `0`。

| 验证项 | 实际结果 | 结论 |
|---|---|---|
| 网关健康检查 | HTTP 200 | 通过 |
| 无 Token 访问业务接口 | HTTP 401，确认由网关拦截 | 通过 |
| 无 Token 但伪造 ADMIN 身份头 | HTTP 401 | 通过 |
| USER、ADMIN 登录 | 均 HTTP 200，角色分别为 USER、ADMIN | 通过 |
| USER Token 访问普通业务 API | HTTP 200 | 通过 |
| USER Token 搭配伪造 ADMIN 头创建用户资料 | HTTP 403，确认由网关拦截 | 通过 |
| USER 访问认证账号列表 | HTTP 403 | 通过 |
| ADMIN 访问认证账号列表 | HTTP 200 | 通过 |
| ADMIN 创建卖家、买家资料 | 均 HTTP 201 | 通过 |
| 伪造身份头覆盖 | 下游 username=demo-user、forwardedUser=demo-user、forwardedRoles=USER | 通过 |
| 旧式身份头清理 | X-User-Id、X-Role 均未到达下游 | 通过 |
| 篡改 JWT 签名后访问 | HTTP 401，确认由网关拦截 | 通过 |
| 携带 Token 发布教材、创建订单 | 均 HTTP 201 | 通过 |
| 查询订单、教材 | 均 HTTP 200，订单编号匹配，库存由 3 降至 2 | 通过 |
| 下游宿主机端口检查 | 两个 user-service 实例及 textbook/order 服务均输出 NO_HOST_PORT | 通过 |
| 脚本退出状态 | ALL GATEWAY AUTH CHECKS PASSED，退出码 0 | 通过 |

本次业务流程创建的订单 ID 为 `21`，买家资料 ID 为 `12`，教材 ID 为 `8`，订单状态为 `PENDING_PAYMENT`。以上结果来自本人实际运行输出，脚本没有打印密码或完整 Token。

防绕过措施为取消下游宿主机端口映射，让普通外部客户端统一通过网关。Docker 内网、Docker 管理权限及容器内访问属于可信运维边界；该措施不等同于下游全部独立实现 JWT 校验。

本步实现的是角色级授权，尚未建立认证账号与业务 buyerId/sellerId 的归属关联，不将本次验证描述为已实现对象级权限控制。完整认证流程、角色权限、身份头处理和路由表见 `docs/security.md`。
