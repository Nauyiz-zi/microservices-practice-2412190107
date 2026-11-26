# 第十周：Nacos Config 配置管理

## 1. 配置中心

### 实现思路

用户、教材两个服务添加 Nacos Config 依赖，通过 application-dev.yml 的 spring.config.import 读取服务专属配置。Data ID 分别为 user-service-dev.yaml、textbook-service-dev.yaml，Namespace 为 public（ID 留空），Group 均为 CAMPUS_TEXTBOOK_CONFIG。

集中管理非敏感 Hikari 连接池最大连接数（分别7、8）、最小空闲连接数（均2）、应用日志级别与验证标记。真实数据库密码、JWT 密钥与账号密码继续留在本地安全配置中，不上传到示例或 Git。

服务提供固定白名单的配置读取接口，经已有网关鉴权访问。接口同时返回配置值和 HikariDataSource 实际属性，scripts/config-test.sh 校验服务名、dev profile、远程标记、配置与实际连接池值一致。配置文件与操作详见 docs/configuration.md。

### 运行命令

先在 Nacos public 下发布 config/nacos/dev/ 两个 YAML 示例，再执行：

```powershell
docker compose config --quiet
mvn -f .\services\user-service\pom.xml test
mvn -f .\services\textbook-service\pom.xml test
docker compose build user-service textbook-service
docker compose up -d --no-deps --force-recreate --scale user-service=2 user-service
# 等用户服务均 healthy 后继续
docker compose up -d --no-deps --force-recreate textbook-service
docker compose ps
```

全部相关服务 healthy 后，在 Git Bash 中执行：

```bash
bash scripts/config-test.sh
echo "config-test exit code: $?"
```

### 验证结果

已在本人实际 Docker 与 Nacos 环境中完成验证。Nacos public 命名空间的配置列表显示以下两条 YAML 配置，Group 均为 `CAMPUS_TEXTBOOK_CONFIG`：

| 服务             | Data ID                   | 远程验证标记          |
| ---------------- | ------------------------- | --------------------- |
| user-service     | user-service-dev.yaml     | nacos-user-dev-v1     |
| textbook-service | textbook-service-dev.yaml | nacos-textbook-dev-v1 |

两个用户服务实例和一个教材服务实例均为 healthy。通过网关运行 `scripts/config-test.sh`，读取接口均返回 HTTP 200，实际结果如下：

| 验证项                               | user-service      | textbook-service      |
| ------------------------------------ | ----------------- | --------------------- |
| 运行环境                             | dev               | dev                   |
| 远程标记                             | nacos-user-dev-v1 | nacos-textbook-dev-v1 |
| 配置最大连接数 configuredMaxPoolSize | 7                 | 8                     |
| 实际最大连接数 actualMaxPoolSize     | 7                 | 8                     |
| 实际最小空闲数 actualMinimumIdle     | 2                 | 2                     |
| 配置值与实际组件一致                 | 通过              | 通过                  |

脚本最终输出 `ALL CONFIG READ CHECKS PASSED`，紧接着查询 `config-test exit code` 为 `0`。这说明两个服务已读取远程配置，且所选连接池参数实际应用到了 HikariDataSource。

验证过程中曾出现读取结果为 `local-unconfigured`、配置最大连接数为 `-1`、实际连接池使用默认值 `10` 的情况；启动日志提示两个 Data ID 的配置为空。核对并在 public 命名空间、正确 Group 下发布配置，再重启相关服务后，重新执行脚本通过。

本步验证启动时集中读取，`refreshEnabled=false`。参数修改后需要重启相关服务；多环境隔离和动态变更验证将在后续小题完成，本节不将连接池参数描述为已支持热更新。
