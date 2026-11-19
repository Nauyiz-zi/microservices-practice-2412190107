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
