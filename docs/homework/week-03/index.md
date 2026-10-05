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
- [x] 启动应用并验证 `/api/hello` 返回 JSON 状态信息。
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

## 测试命令与结果

测试命令：

```bash
./mvnw test
```

测试结果：

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

测试说明：`MonolithApplicationTests` 使用 `@SpringBootTest` 加载完整的 Spring 应用上下文，`contextLoads` 测试通过，说明启动类、`application.yml`、Spring Web MVC、Actuator 和相关组件均能被 Spring 容器正常扫描和加载。

## 本周完成内容

- 工程目录：`monolith/`
- 启动类：`com.zjgsu.zy.MonolithApplication`
- 配置文件：`src/main/resources/application.yml`
- 问候接口：`GET /api/hello`
- 健康检查：`GET /actuator/health`
- 启动测试：`MonolithApplicationTests.contextLoads`
- 项目提案：`docs/project-proposal.md`
- 根 README：已补充 Java 25、Maven Wrapper、启动和测试命令、接口访问地址和尚未实现的业务能力
- 运行步骤：在 `monolith/` 下执行 `./mvnw test`，然后执行 `./mvnw spring-boot:run`
- 验证结果：`BUILD SUCCESS`，`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`
- 接口结果：`/api/hello` 返回项目名称 JSON；`/actuator/health` 返回 `{"groups":["liveness","readiness"],"status":"UP"}`
