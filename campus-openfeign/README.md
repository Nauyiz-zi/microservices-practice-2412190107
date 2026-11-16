# 校园选课微服务：OpenFeign 改造

本目录独立保存第 07 章校园选课微服务练习，包括课程、学生、选课三个 Spring Boot 服务。

- `student-service`：学生查询，默认端口 8082。
- `course-service`：课程查询、原子占用和释放名额，默认端口 8081。
- `enrollment-service`：OpenFeign 调用学生与课程服务，选课占位、退课释放、写入失败补偿，默认端口 8083。

## 构建与验证

需要 Java 25、Maven 和 Python 3。以下命令在本目录执行：

```powershell
mvn -B -f student-service/pom.xml package
mvn -B -f course-service/pom.xml package
mvn -B -f enrollment-service/pom.xml package
python enrollment-service/scripts/verify-openfeign.py --root . --output verification
```

验证脚本启动三个真实服务，在隔离端口 28081–28083 上使用静态服务发现配合 Feign 和 LoadBalancer 调用；验证结束后停止自己启动的进程。端口占用时可添加 `--base-port 38081`。

正常部署使用各服务原有的 Nacos 配置，默认地址 `127.0.0.1:8848`、分组 `COURSE_GROUP`，通过环境变量 `NACOS_SERVER_ADDR`、`NACOS_USERNAME`、`NACOS_PASSWORD` 配置注册中心。

## 已完成验证

6 项单元测试和 25 项真实 HTTP 检查通过，包括选课 201、退课 204、重复/满员 409、并发竞争不超额、依赖服务停止后 503、退课失败保留原记录。

- [完整改造说明及一致性边界](docs/OpenFeign改造与验证说明.md)
- [2026-11-15 HTTP 验证结果](docs/verification-results.json)
- [单元测试结果](docs/unit-test-results.txt)

本次验证覆盖依赖服务不可用场景，未实测 Nacos 在线注册或多实例分流。当前 H2 为进程内存库，多实例部署需要共享数据库。远程超时、进程崩溃等跨服务一致性问题仍需生产级幂等和持久化补偿机制。
