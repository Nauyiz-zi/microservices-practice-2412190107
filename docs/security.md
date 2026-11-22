# 认证与网关安全设计

## 认证流程

客户端统一请求本机映射的网关端口 8080。健康检查、登录和公开 JWKS 无需登录；其他业务 API 必须携带 `Authorization: Bearer <access_token>`。

1. `POST /api/auth/login` 通过网关转到 `user-service`，账号密码使用 BCrypt cost=12 校验，数据库只存储密码哈希。
2. 用户服务以 RSA 私钥签发 RS256 JWT，包含 `sub`、`roles`、`iss`、`aud`、`iat`、`nbf`、`exp`、`jti`，有效期 900 秒。
3. 网关仅挂载 RSA 公钥，固定接受 RS256，验证签名、有效期、issuer、audience 和必要声明，再按角色授权。无 Token 或 Token 无效返回 401；有效 Token 角色不足返回 403。
4. 网关完成身份头清理与重建，通过 Nacos 和 `lb://服务名` 转发请求，保留 Bearer Token。用户服务的认证接口再次验签并进行 ADMIN 角色校验。

测试账号为 `demo-user / USER` 和 `demo-admin / ADMIN`。密码由本人本地设置并在脚本中安全输入，代码和文档不保存明文密码；账号与角色在 README 中列出。JWT 私钥和真实 Token 不提交到 GitHub。

## 网关路由与角色权限

规则按表中顺序匹配。USER、ADMIN 均可访问普通业务 API，创建用户资料和查看认证账号列表要求 ADMIN。

| 方法与路径 | 路由目标 | 访问要求 |
|---|---|---|
| GET `/actuator/health`、健康探针子路径 | 网关本身 | 公开 |
| POST `/api/auth/login` | `lb://user-service` | 公开 |
| GET `/.well-known/jwks.json` | `lb://user-service` | 公开，仅发布公钥 |
| `/api/auth/accounts` | `lb://user-service` | ADMIN；用户服务再次校验 |
| POST `/api/users`、`/api/users/` | `lb://user-service` | ADMIN |
| 其他 `/api/auth/**`、`/api/users`、`/api/users/**` | `lb://user-service` | USER 或 ADMIN |
| `/api/textbooks`、`/api/textbooks/**` | `lb://textbook-service` | USER 或 ADMIN |
| `/api/orders`、`/api/orders/**` | `lb://order-service` | USER 或 ADMIN |
| 其他路径，包括 `/internal/**` | 不向外转发 | 拒绝 |

网关返回的认证/授权错误带有 `X-Auth-Enforcer: gateway`，便于区分网关拦截和下游业务错误。网关不使用 Session、表单登录或 HTTP Basic；通过请求头携带 Bearer Token。

本步实现角色级访问控制。原业务对象仍使用 buyerId、sellerId 等业务资料编号，认证账号与业务资料尚未建立归属映射，因此本步不宣称实现“用户只能操作本人订单/教材”的对象级授权。

## 防止伪造身份请求头

`TrustedIdentityFilter` 在路由转发前按不区分大小写方式删除以下客户端请求头，重复值也会整体删除：

- 所有 `X-User-*`、`X-Auth-*`、`X-Authenticated-*`；
- `X-Username`、`X-Role`、`X-Roles`、`X-Forwarded-User`、`X-Forwarded-Roles`。

认证成功后仅从网关验签得到的 JWT 主体重建 `X-Authenticated-User` 和 `X-Authenticated-Roles`。这些头是转发上下文，不是客户端可声明的授权凭据。

`GET /api/auth/request-context` 同时返回用户服务再次验签得到的身份与网关转发头，供核验覆盖行为。传入伪造 ADMIN 身份头且携带 USER Token 时，应返回真实 USER 身份；伪造的旧式 `X-User-Id`、`X-Role` 不应到达下游。该接口不返回 Token、密码哈希或私钥。

未携带 Token 时，即使伪造 ADMIN 请求头仍返回 401；USER Token 即使搭配 ADMIN 请求头也不能通过 ADMIN 规则。

## 避免外部客户端绕过网关

`compose.yaml` 取消 user-service、textbook-service、order-service 的宿主机 `ports` 映射，仅保留各容器内部 8080 端口。三个服务仍在 `campus_net` 内通信；业务对外入口仅为网关的 `127.0.0.1:8080`。Nacos 管理入口独立存在，不作为业务 API 入口。

因此普通外部客户端无法再通过原 8082、8083 或用户服务动态宿主机端口直接调用业务容器。验证时检查实际运行容器的 `NetworkSettings.Ports`，三个业务服务的所有端口都没有 HostPort 绑定。仅修改 YAML 不算完成，必须重建容器使设置生效。

当前安全边界是受信任的 Docker 桥接内网：textbook/order 服务依赖网关鉴权和端口隔离，未另行实现 JWT 验签；用户服务的认证接口有独立 JWT 校验。拥有 Docker 管理权限、可进入容器或加入该网络的主体属于可信运维范围，取消端口映射不防御此类主体。部署到共享或不可信内网时，还需要下游独立鉴权、网络策略或服务间 mTLS。

## 密钥与部署

- 用户服务挂载本地 `work/jwt/private.pem` 与 `public.pem`；网关仅挂载 `public.pem`，均只读。
- 网关与认证服务使用同一组公私钥、issuer `campus-textbook-auth`、audience `campus-api`。
- 密钥、真实 Token 和 `.env` 不提交；密码哈希通过本地环境配置提供。
- 网关公钥变更需要同步更新并重建网关；本步不实现自动密钥轮换。
- 作业环境默认本机 HTTP；正式远程部署应在入口启用 HTTPS，避免 Bearer Token 被窃取。

## 验证方法

在仓库根目录执行：

```powershell
mvn -f .\services\gateway-service\pom.xml test
mvn -f .\services\user-service\pom.xml test
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\gateway-auth-smoke.ps1
```

脚本验证无 Token 401、Token 正常访问、USER 越权 403、ADMIN 放行、伪造身份头覆盖、篡改 Token 401，以及带 Token 发布教材、下单、查询和库存扣减，最后检查实际容器没有业务宿主机端口绑定。成功退出码为 0，任一断言失败立即打印 FAIL 并以 1 退出。每次使用唯一编号创建测试数据，可重复运行。

本步收回业务宿主机端口后，Week 08 依赖直接服务端口的旧 E2E 命令不再适用。应通过网关并携带 Token 执行本周验证；后续“自动测试”任务再统一更新旧脚本。
