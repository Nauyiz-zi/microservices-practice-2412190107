## 第七周：Nacos 部署与服务注册

### 一、本周目标

在第六周拆分微服务的基础上接入 Nacos，保留原有三个业务服务、独立数据库、已有接口和业务规则。订单服务调用教材服务时，通过 Nacos 按服务名发现实例。

### 二、部署与初始化

本项目使用 `nacos/nacos-server:v3.2.4`，以单机模式运行。Nacos 数据通过命名卷 `nacos_data` 持久化，服务端鉴权已开启。

首次启动前，在仓库根目录根据 `.env.example` 准备 `.env`，填写数据库密码、Nacos Token、Server identity 以及 Nacos 用户名和密码。

在仓库根目录执行：

```powershell
docker compose config --quiet
docker compose up -d nacos
docker compose logs -f nacos
```

等待 Nacos 启动后，访问控制台：

```text
http://localhost:18081/
```

首次访问时按照控制台提示初始化管理员密码，并确保业务服务使用的 Nacos 账号密码与本地 `.env` 配置一致。初始化完成后，启动三个业务服务及其数据库：

```powershell
docker compose up -d --build user-service textbook-service order-service
docker compose ps
```

查看业务服务日志：

```powershell
docker compose logs --tail=150 user-service textbook-service order-service
```

### 三、实际访问端口

| 用途 | 容器端口 | 宿主机端口 | 说明 |
|---|---:|---:|---|
| Nacos 控制台 | 8080 | 18081 | 浏览器访问 `http://localhost:18081/` |
| Nacos 服务端 | 8848 | 18848 | 业务容器使用 `nacos:8848` |
| Nacos SDK gRPC | 9848 | 19848 | 客户端与 Nacos 通信 |
| 用户服务 | 8080 | 8081 | `http://localhost:8081` |
| 教材服务 | 8080 | 8082 | `http://localhost:8082` |
| 订单服务 | 8080 | 8083 | `http://localhost:8083` |

Nacos 的宿主机端口只绑定到 `127.0.0.1`，不暴露到公网。三个业务服务通过 Compose 网络访问 `nacos:8848`，使用各自的数据库。

### 四、服务注册结果

三个服务使用各自的 `spring.application.name`，均注册到 `DEFAULT_GROUP`：

| 业务服务 | Nacos 服务名 | 分组 | 容器端口 |
|---|---|---|---:|
| 用户服务 | `user-service` | `DEFAULT_GROUP` | 8080 |
| 教材服务 | `textbook-service` | `DEFAULT_GROUP` | 8080 |
| 订单服务 | `order-service` | `DEFAULT_GROUP` | 8080 |

在 Nacos 控制台进入“服务管理 → 服务列表”，可看到三个业务服务及其健康实例。点击服务详情，可查看实例 IP、端口和健康状态。

| 服务名 | 实例 IP | 端口 | 状态 |
|---|---|---:|---|
| `user-service` | `172.20.0.5` | 8080 | 健康 |
| `textbook-service` | `172.20.0.6` | 8080 | 健康 |
| `order-service` | `172.20.0.8` | 8080 | 健康 |

以上 IP 是截图时的容器地址，容器重建后可能变化，以 Nacos 控制台当前显示结果为准。

### 五、服务发现与下线验证

订单服务使用 `DiscoveryClient` 按 `textbook-service` 查询实例，并使用发现到的实例地址查询教材和请求库存扣减。正常运行时，订单业务流程已验证通过。

停止教材服务：

```powershell
docker compose stop textbook-service
```

等待实例状态更新后，再次通过订单服务创建订单，返回：

```text
HTTP status: 503
code: DEPENDENCY_UNAVAILABLE
message: 教材服务没有可用实例，请稍后重试
```

重新启动教材服务：

```powershell
docker compose up -d textbook-service
docker compose ps textbook-service
```

等待服务显示 `healthy`，并在 Nacos 控制台确认实例重新注册。恢复后的订单调用仍需再次验证并记录结果。

### 六、健康检查

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
Invoke-RestMethod http://localhost:8082/actuator/health
Invoke-RestMethod http://localhost:8083/actuator/health
```

三个服务正常就绪时，响应中的状态应为 `UP`。

### 七、测试命令

分别进入各服务目录运行测试：

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

上述三个 `cd` 命令均以仓库根目录为起点。订单服务测试使用 H2 和 HTTP 桩，不依赖真实 MySQL；测试配置中的教材客户端使用测试地址，不通过真实 Nacos 查找教材服务。
