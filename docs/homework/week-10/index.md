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

## 2. 多环境配置

### 实现思路

用户服务和教材服务分别提供 dev、test 两套 Nacos 配置。
dev 用于本地开发演示，test 用于测试演示。
两套配置使用不同 Data ID 和 Group，并设置不同验证标记与连接池参数。

开发配置使用 CAMPUS_TEXTBOOK_CONFIG，
测试配置使用 CAMPUS_TEXTBOOK_CONFIG_TEST。
完整 Data ID、Group 与用途见 docs/configuration.md。

Compose 通过 APP_PROFILE 选择运行环境，默认 dev。
application-dev.yml、application-test.yml 分别导入对应远程配置。

原 Maven 自动测试环境改名为 unit，使用 H2、临时 JWT 密钥，
关闭 Nacos Config 和服务发现，避免自动测试连接真实配置中心。

dev/test 是同一套容器的顺序配置切换，演示数据库仍沿用现有数据库；
unit 使用独立 H2，不将配置隔离描述为数据库隔离。

### 运行命令

先发布两份 test 配置，并运行两个服务的 Maven 测试及镜像构建。

PowerShell 中切换到 test：

```powershell
$env:APP_PROFILE = "test"
docker compose up -d --no-deps --force-recreate --scale user-service=2 user-service
```

等待用户服务 healthy 后执行：

```powershell
docker compose up -d --no-deps --force-recreate textbook-service
```

服务均 healthy 后，在 Git Bash 验证：

```bash
CONFIG_PROFILE=test \
EXPECTED_USER_POOL=3 \
EXPECTED_TEXTBOOK_POOL=4 \
EXPECTED_USER_MARKER=nacos-user-test-v1 \
EXPECTED_TEXTBOOK_MARKER=nacos-textbook-test-v1 \
bash scripts/config-test.sh

echo "config-test exit code: $?"
```

在原 PowerShell 窗口切回 dev：

```powershell
$env:APP_PROFILE = "dev"
docker compose up -d --no-deps --force-recreate --scale user-service=2 user-service
```

等待用户服务 healthy 后执行：

```powershell
docker compose up -d --no-deps --force-recreate textbook-service
```

服务均 healthy 后，在 Git Bash 验证：

```bash
bash scripts/config-test.sh
echo "config-test exit code: $?"
```

### 验证结果

已在实际 Docker 环境中完成 test 环境验证，并切回 dev 再次验证。

| 环境 | 服务 | 远程配置标记 | 配置最大连接数 | 实际最大连接数 | 实际最小空闲数 |
|---|---|---|---:|---:|---:|
| test | user-service | nacos-user-test-v1 | 3 | 3 | 2 |
| test | textbook-service | nacos-textbook-test-v1 | 4 | 4 | 2 |
| dev | user-service | nacos-user-dev-v1 | 7 | 7 | 2 |
| dev | textbook-service | nacos-textbook-dev-v1 | 8 | 8 | 2 |

两个环境下，网关健康检查、用户登录和配置读取接口均返回 HTTP 200。
config-test.sh 校验运行环境、远程标记、配置值及实际 Hikari 参数，
两次均输出 ALL CONFIG READ CHECKS PASSED。
切回 dev 后立即查询脚本退出码，结果为 0。

### 关键结论

dev 与 test 分别读取不同 Data ID 和 Group 下的配置。
切换运行 profile 并重建相关服务后，验证标记和实际连接池参数
随环境正确变化，证明服务读取了所选环境的配置。

本次验证的是环境切换后的启动读取，不属于动态刷新验证。
Maven 自动测试使用独立 unit 环境，其测试结果另行据实记录。

## 3. 动态配置验证

### 实现思路

教材服务新增独立业务配置 textbook-business-dev.yaml，
开发 Group 为 CAMPUS_TEXTBOOK_CONFIG；
测试演示配置为 textbook-business-test.yaml，
Group 为 CAMPUS_TEXTBOOK_CONFIG_TEST。

业务 Data ID 开启 refreshEnabled=true，
StockWarningPolicy 使用 @RefreshScope 读取库存预警阈值和版本。
原连接池 Data ID 保持启动时读取。

新增库存预警判断预览接口，固定传入库存5，
将阈值从3改为10，预期预警结果从false变为true。

### 运行命令

完成首次代码部署、服务健康后，在 Git Bash 执行：

```bash
CONFIG_MODE=dynamic CONFIG_PROFILE=dev bash scripts/config-test.sh
echo "config-test exit code: $?"
```

脚本采集基线后，在 Nacos 将开发业务配置中的阈值改为10、
版本改为v2并发布。期间不构建镜像、不重启或重建容器。

### 验证结果

在 dev 环境中运行 config-test.sh 的动态验证模式。
采集基线后，在 Nacos 修改业务配置并发布：

- Namespace：public
- Group：CAMPUS_TEXTBOOK_CONFIG
- Data ID：textbook-business-dev.yaml

实际结果如下：

| 验证项 | 修改前 | 修改后 | 结论 |
|---|---|---|---|
| 固定库存 | 5 | 5 | 输入保持一致 |
| 库存预警阈值 | 3 | 10 | 动态生效 |
| 配置版本 | v1 | v2 | 动态生效 |
| 预警判断 | false | true | 业务判断随配置变化 |
| 实例标识 instance | 基线实例 | 与基线一致 | 实例未改变 |
| 进程号 pid | 1 | 1 | 保持一致 |
| JVM启动时间 startedAt | 基线启动时间 | 与基线一致 | JVM未重启 |

脚本输出 SAME_JVM，确认 instance、pid、startedAt 均保持不变。
最终输出 ALL DYNAMIC CONFIG CHECKS PASSED，
紧接着查询脚本退出码，结果为0。

### 关键结论

Nacos 发布配置后，教材服务在同一实例、同一 JVM 中读取到新阈值，
库存预警判断随之改变，证明业务配置可以动态生效，
无需重新打包或重启服务。

本次验证针对库存预警业务参数，不将其结论扩展到连接池等其他配置。
