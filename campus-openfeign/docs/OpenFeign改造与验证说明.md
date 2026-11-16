# 校园选课微服务：OpenFeign 改造与验证

改造位置：`D:\microservices\enrollment-service`、`D:\microservices\course-service`。学生服务接口保持原样。项目原有版本为 Java 25、Spring Boot 4.0.8、Spring Cloud Alibaba 2025.1.0.0；选课服务引入 Spring Cloud BOM 2025.1.0 管理 OpenFeign 和 LoadBalancer。

## 代码变化

- 选课服务启动类添加 `@EnableFeignClients`。
- 新增 `StudentClient`、`CourseClient`，通过 `@FeignClient(name = "student-service")` 和 `@FeignClient(name = "course-service")` 调用服务。删除手动 `DiscoveryClient` 取第一个实例以及 `RestTemplate` 调用。
- 正常运行继续使用现有 Nacos 配置，服务注册与发现位于 `COURSE_GROUP`；LoadBalancer 负责选择实例。
- 连接超时 1500 ms、读取超时 3000 ms；Feign 使用 `Retryer.NEVER_RETRY`，LoadBalancer 重试关闭，避免写操作超时后被自动重复执行。
- 原始代码没有名额操作和退课接口，因此补齐课程名额计数、占用/释放接口和退课接口。课程响应新增 `selectedCount`、`remainingSeats`。
- 课程占用通过 `selectedCount < capacity` 的条件更新实现，释放通过 `selectedCount > 0` 的条件更新实现，避免超额选课和计数变成负数。

关键文件（相对各服务目录）：

| 服务 | 文件 | 用途 |
|---|---|---|
| enrollment-service | `pom.xml` | OpenFeign、LoadBalancer 和测试 JVM 配置 |
| enrollment-service | `src/main/java/com/zjgsu/zy/client/StudentClient.java` | 查询学生 |
| enrollment-service | `src/main/java/com/zjgsu/zy/client/CourseClient.java` | 查询课程、占位、释放 |
| enrollment-service | `src/main/java/com/zjgsu/zy/controller/EnrollmentController.java` | 选课、退课、远程异常映射和补偿 |
| enrollment-service | `src/main/java/com/zjgsu/zy/repository/EnrollmentRepository.java` | 重复检查、退课行锁 |
| enrollment-service | `src/main/java/com/zjgsu/zy/config/FeignConfiguration.java` | 关闭 Feign 重试 |
| enrollment-service | `src/main/resources/application.yml` | 超时、重试与错误消息 |
| enrollment-service | `src/test/java/com/zjgsu/zy/controller/EnrollmentControllerTest.java` | 6 项单元测试 |
| enrollment-service | `scripts/verify-openfeign.py` | 真实服务 HTTP 联调脚本 |
| course-service | `src/main/java/com/zjgsu/zy/model/Course.java` | 名额计数 |
| course-service | `src/main/java/com/zjgsu/zy/repository/CourseRepository.java` | 原子占用与释放 |
| course-service | `src/main/java/com/zjgsu/zy/controller/CourseController.java` | 名额操作 HTTP 接口 |

## 业务流程与接口

选课 `POST /api/enrollments`，请求 `{"studentId":1,"courseId":1}`：检查参数 → Feign 查询学生和课程 → 检查重复选课 → Feign 占用名额 → 保存选课记录 → 返回 201。数据库保存失败时调用释放接口补偿；唯一约束冲突返回 409。补偿失败返回 503 并记录需要对账的日志。

退课 `DELETE /api/enrollments/{id}`：锁定选课记录 → Feign 释放名额 → 删除记录 → 返回 204。释放请求失败则不删除记录。行锁用于防止同一记录被并发退课而重复释放。

课程内部接口：`POST /api/courses/{id}/reserve`、`POST /api/courses/{id}/release`，成功均为 204；课程不存在返回 404；满员或没有已占名额可释放返回 409。

选课服务错误映射：参数非法为 400；学生/课程不存在为 422；重复选课或满员为 409；远程连接失败、超时或依赖服务错误为 503；本地选课记录不存在为 404。

## 构建和复现

在 PowerShell 中执行：

```powershell
mvn -B -f D:\microservices\student-service\pom.xml package
mvn -B -f D:\microservices\course-service\pom.xml package
mvn -B -f D:\microservices\enrollment-service\pom.xml package
python D:\microservices\enrollment-service\scripts\verify-openfeign.py --root D:/microservices --output D:/microservices/enrollment-service/verification
```

脚本使用隔离端口 28081、28082、28083 和各服务原有内存 H2 数据库。可用 `--base-port 38081` 改变起始端口。脚本检查端口占用，启动三个真实 Java 服务，完成请求和断言，并在结束时停止自己启动的进程。

为使验证不依赖外部 Nacos，本次脚本关闭 Nacos discovery，并通过 Spring 的 SimpleDiscoveryClient 注册本地课程、学生实例。Feign **没有设置固定 URL**，实际仍通过服务名和 Spring Cloud LoadBalancer 调用真实服务。本次验证选择“依赖服务不可用时的响应”，没有声称验证 Nacos 在线注册或多实例负载均衡。

正常使用 Nacos 时，在三个终端中分别启动下列命令，使用现有 8081/8082/8083 端口；确保 Nacos 已启动，并按环境设置 `NACOS_SERVER_ADDR`、`NACOS_USERNAME`、`NACOS_PASSWORD`：

```powershell
java -jar D:\microservices\course-service\target\course-service-0.1.0-SNAPSHOT.jar
java -jar D:\microservices\student-service\target\student-service-0.1.0-SNAPSHOT.jar
java -jar D:\microservices\enrollment-service\target\enrollment-service-0.1.0-SNAPSHOT.jar
```

## 验证结果

三项 Maven 构建成功；选课服务 6 项单元测试全部通过。单元测试覆盖先占位后保存、保存失败释放补偿、补偿失败返回 503、满员不写入记录、释放失败不删除记录、先释放后删除。

真实服务联调包含 25 项 HTTP 状态断言及名额计数断言：

| 场景 | 实际结果 |
|---|---|
| 创建学生与容量为 1 的课程 | 201 |
| studentId 为 0 | 400 |
| 学生或课程不存在 | 422 |
| 正常选课 | 201，selectedCount 从 0 变为 1 |
| 重复选课 | 409，名额保持 1 |
| 另一学生选择已满课程 | 409，名额保持 1 |
| 正常退课 | 204，selectedCount 从 1 恢复为 0 |
| 查询已退记录、重复退课 | 404 |
| 退课后重新选课 | 201 |
| 两个学生并发争抢一个名额 | 一个 201、一个 409，selectedCount 为 1 |
| 停止课程服务后选课 | 503 |
| 停止课程服务后退课 | 503，随后查询原选课记录仍为 200 |
| 停止学生服务后选课 | 503 |

详细响应体、耗时和状态在同目录 `verification/results.json`；三个服务启动日志在 `verification/`；单元测试和构建日志在 `build-logs/`。

## 一致性边界

这是本地数据库事务加远程调用补偿的教学实现。HTTP 超时可能发生在远程提交之后；进程崩溃、远程释放成功后本地删除提交失败、补偿失败等情况仍可能导致两边状态不一致。关闭自动重试可以减少重复写入，不能提供分布式原子性。生产环境需要进一步加入以业务操作号为键的幂等占位/释放、持久化操作状态、补偿任务和对账。

当前 H2 为进程内存库，重启会清空数据；课程多实例部署必须使用共享数据库，不能把多个独立 H2 实例当作同一份课程名额数据。

版本和配置参考：[Spring Cloud 2025.1.0 发布说明](https://spring.io/blog/2025/11/25/spring-cloud-2025-1-0-aka-oakwood-has-been-released/)、[Spring Cloud OpenFeign 官方文档](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html)。
