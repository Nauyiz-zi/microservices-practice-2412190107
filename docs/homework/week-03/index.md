## 本周计划

- [x] 确定项目题目为校园二手教材交易平台。
- [x] 完成 `monolith/` 工程目录创建。
- [x] 配置 Java 25 和 Spring Boot 4.0.x。
- [x] 配置 Maven Wrapper，支持 `./mvnw test` 和 `./mvnw spring-boot:run`。
- [x] 统一 Group 和 Package name 为 `com.zjgsu.zy`。
- [x] 将配置文件统一为 `src/main/resources/application.yml`。
- [x] 设置应用名称为 `monolith`，默认端口为 `8080`。
- [x] 实现 `GET /api/hello` 状态检查接口。
- [x] 完成 Spring Boot 启动测试 `MonolithApplicationTests`。
- [x] 启动应用并验证 `/api/status` 返回 JSON 状态信息。
- [x] 更新 `monolith/readme.md`，写明环境要求、运行命令和端口。
- [x] 新增 `docs/project-proposal.md`，完成选题、目标用户、优先业务场景和两个核心模型的规划。

## 启动与测试命令

Git Bash：

```bash
cd /d/microservices/microservices-practice-2412190107/monolith
./mvnw test
./mvnw spring-boot:run
```

Windows PowerShell：

```powershell
cd D:\microservices\microservices-practice-2412190107\monolith
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

## 接口验证

请求地址：

```text
GET http://localhost:8080/api/hello
```

返回结果：

```json
{"message":"校园二手教材交易平台","application":"monolith"}
```

请求地址：

```text
GET http://localhost:8080/actuator/health
```

返回结果：

```json
{"groups":["liveness","readiness"],"status":"UP"}
```
