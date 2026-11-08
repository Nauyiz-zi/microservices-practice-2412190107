# 第七周：Nacos 服务注册与发现

## 一、部署、初始化与服务注册

项目使用 `nacos/nacos-server:v3.2.4` 单机部署 Nacos，开启鉴权，并通过 Docker 命名卷 `nacos_data` 持久化数据。真实凭据保存在本地 `.env` 中。

首次启动和初始化：

```powershell
docker compose config --quiet
docker compose up -d nacos
docker compose logs -f nacos
```

访问 `http://localhost:18081/`，按照提示初始化管理员密码。初始化完成后启动业务服务及必要数据库：

```powershell
docker compose up -d --build user-service textbook-service order-service
docker compose ps
```

实际端口如下：

| 服务 | 本机端口 | 容器端口 |
| --- | ---: | ---: |
| Nacos 控制台 | 18081 | 8080 |
| Nacos 服务端 | 18848 | 8848 |
| Nacos SDK gRPC | 19848 | 9848 |
| 用户服务 | 8081 | 8080 |
| 教材服务 | 8082 | 8080 |
| 订单服务 | 8083 | 8080 |

业务服务在 Compose 网络中通过 `nacos:8848` 连接 Nacos。三个业务服务均注册到默认命名空间 `public`，分组为 `CAMPUS_TEXTBOOK_GROUP`：

| 服务名 | 注册结果 |
| --- | --- |
| `user-service` | 已注册，实例健康 |
| `textbook-service` | 已注册，实例健康 |
| `order-service` | 已注册，实例健康 |

教材服务在本次验证中显示的实例地址为 `172.20.0.6:8080`；容器重建后 IP 可能变化，以控制台实际显示为准。

## 二、服务发现与业务调用

### 调用方向

订单服务创建订单时，先校验买家，再通过 `DiscoveryClient.getInstances("textbook-service")` 获取教材服务实例，使用实例 URI 查询教材并请求扣减库存，成功后保存本地订单。

服务发现使用默认命名空间 `public` 和分组 `CAMPUS_TEXTBOOK_GROUP`。各服务通过环境变量读取 Nacos 地址和凭据。每次教材查询或库存扣减时获取当前已知实例，保留 HTTP 连接和读取超时。

### 请求示例

```text
POST http://localhost:8083/api/orders
Content-Type: application/json
```

```json
{
  "requestId": "{{$guid}}",
  "buyerId": 1,
  "textbookId": 2,
  "quantity": 1
}
```

`{{$guid}}` 为 Postman 动态变量，发送时生成请求编号。

### 预期结果与实际结果

| 场景 | 预期结果 | 实际结果 |
| --- | --- | --- |
| 教材服务正常，参数有效 | 成功创建订单 | HTTP `201 Created`，状态 `PENDING_PAYMENT` |
| 教材服务无可用实例 | 返回依赖不可用错误，不保存订单 | HTTP `503 Service Unavailable`，错误码 `DEPENDENCY_UNAVAILABLE` |

订单服务通过实例地址完成教材查询和库存扣减。实例列表为空和服务发现异常的情况由模拟 `DiscoveryClient` 的测试覆盖。

## 三、健康状态、下线与恢复

### 健康检查

三个业务服务保留 `/actuator/health` 端点：

```text
http://localhost:8081/actuator/health
http://localhost:8082/actuator/health
http://localhost:8083/actuator/health
```

正常运行时，三个接口均返回 HTTP `200`，状态为 `UP`。

### 正常调用与数据检查

使用第二部分的请求创建一笔订单，购买数量为 `1`，返回 HTTP `201 Created`。

| 检查项 | 创建前 | 创建后 |
| --- | ---: | ---: |
| 教材库存 | 1 | 0 |
| 本地订单数量 | 6 | 7 |

### 服务下线与失败结果

停止教材服务：

```powershell
docker compose stop textbook-service
```

等待 Nacos 列表更新后，教材服务实例不再显示。使用新的有效请求编号发送创建订单请求，返回：

```text
HTTP 503 Service Unavailable
code: DEPENDENCY_UNAVAILABLE
message: 教材服务没有可用实例，请稍后重试
```

查询订单数量：

```sql
SELECT COUNT(*) AS order_count FROM trade_orders;
```

| 检查项 | 订单数量 |
| --- | ---: |
| 下线失败请求前 | 7 |
| 下线失败请求后 | 7 |

订单数量没有增加，本次依赖校验失败没有绕过规则新增订单。

### 服务恢复

恢复教材服务并查看容器状态：

```powershell
docker compose up -d textbook-service
docker compose ps textbook-service
```

等待教材服务显示 `healthy`，确认 Nacos 中的 `textbook-service` 重新注册。此前的恢复验证中，再次创建订单返回 HTTP `201`，状态为 `PENDING_PAYMENT`。

### 健康状态区别

Nacos SDK 连接存活表示客户端与注册中心的通信状态；Nacos 实例状态表示注册中心记录的实例情况；Actuator 健康状态反映应用自身健康。

Actuator 返回 `DOWN` 不代表 Nacos 一定会自动摘除实例。服务发现也不会消除跨服务写操作可能出现的部分成功窗口。

## 四、验证脚本与测试

### 脚本检查

`scripts/smoke-test.sh` 检查 Nacos 控制台实际映射地址，以及用户、教材、订单服务的健康接口。

在项目根目录的 Git Bash 中执行：

```bash
bash scripts/smoke-test.sh
echo $?
```

实际结果：Nacos 控制台返回 HTTP `200`，三个服务健康接口均返回 HTTP `200` 和 `UP`，脚本退出状态为 `0`。

临时指定不可用地址，验证失败退出：

```bash
env NACOS_CONSOLE_URL=http://127.0.0.1:1/ bash scripts/smoke-test.sh
echo $?
```

实际结果：Nacos 控制台连接检查失败，脚本报告一项检查失败，退出状态为 `1`。该测试不修改实际 Nacos 配置。

### 各服务测试

以下命令分别从项目根目录进入对应服务目录执行：

```powershell
cd services/user-service
mvn test
```

```powershell
cd services/textbook-service
mvn test
```

```powershell
cd services/order-service
mvn test
```

测试使用 H2、模拟对象或 HTTP 桩隔离外部依赖。订单服务的 `TextbookDiscoveryTest` 模拟 `DiscoveryClient`，检查空实例和服务发现异常。

### 整体验证结论

三个业务服务成功注册到 Nacos。订单服务通过发现教材服务实例完成正常下单；教材服务下线后，订单请求返回 `503`，本地订单数量没有增加；服务恢复后，实例重新注册并完成过正常下单验证。

冒烟测试在检查通过时返回 `0`，检查失败时返回非零状态 `1`。
