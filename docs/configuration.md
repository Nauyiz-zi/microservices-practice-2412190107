# Nacos Config 配置说明

## 环境设计

用户服务和教材服务使用三类配置环境：

| 环境 | 用途 | 配置来源 |
|---|---|---|
| dev | 本地开发演示 | Nacos 开发配置 |
| test | 测试演示 | Nacos 测试配置 |
| unit | Maven 自动测试 | src/test/resources/application-unit.yml，使用 H2 |

运行环境通过 APP_PROFILE 选择，由 Compose 传入
SPRING_PROFILES_ACTIVE，默认 dev。

本方案顺序切换同一套业务容器，不同时启动两套 dev/test 服务。
dev/test 演示沿用现有业务数据库，只验证配置环境隔离；
unit 自动测试使用 H2，不依赖真实数据库和 Nacos。

## Data ID、Group 和用途

所有远程配置均位于 public 命名空间，namespace ID 留空。

| 环境 | Data ID | Group | 用途 |
|---|---|---|---|
| dev | user-service-dev.yaml | CAMPUS_TEXTBOOK_CONFIG | 用户服务开发参数，最大连接数7、最小空闲2 |
| dev | textbook-service-dev.yaml | CAMPUS_TEXTBOOK_CONFIG | 教材服务开发参数，最大连接数8、最小空闲2 |
| test | user-service-test.yaml | CAMPUS_TEXTBOOK_CONFIG_TEST | 用户服务演示测试参数，最大连接数3、最小空闲2 |
| test | textbook-service-test.yaml | CAMPUS_TEXTBOOK_CONFIG_TEST | 教材服务演示测试参数，最大连接数4、最小空闲2 |

开发配置示例保存在 config/nacos/dev/，
测试配置示例保存在 config/nacos/test/。
文件必须发布到 Nacos，服务才能读取。

配置内容包含非敏感连接池参数、日志级别和验证标记。
开发标记分别为 nacos-user-dev-v1、nacos-textbook-dev-v1；
测试标记分别为 nacos-user-test-v1、nacos-textbook-test-v1。

## 配置加载与隔离

application-dev.yml 和 application-test.yml 分别通过
spring.config.import 显式导入各自的 Data ID 与 Group。

环境由本地部署参数决定，远程配置不设置 spring.profiles.active，
防止配置内容反过来改变环境选择。

自动测试类使用 @ActiveProfiles("unit")。
application-unit.yml 关闭 Nacos Config 和服务发现，使用 H2。
用户服务在 unit 下使用测试生成的临时 JWT 密钥；
dev/test 部署使用本地只读挂载的密钥。

当前 refreshEnabled=false，切换环境或修改配置后需要重建或重启
相关业务容器。本步骤验证环境选择与配置读取，不宣称已完成动态刷新。

## 安全边界

.env、数据库密码、账号密码哈希和 JWT 私钥不提交到 Git。
远程示例只保存非敏感配置。

配置 Group 与服务发现 Group 分开管理。
现有服务发现 Group 保持 CAMPUS_TEXTBOOK_GROUP，
不能将其误填为配置 Group。

配置读取接口经过现有网关鉴权，仅返回固定的非敏感字段，
不开放任意环境变量、数据库连接地址或密钥查询。

## 验证方法

通过已有 scripts/config-test.sh 验证配置值和实际 Hikari 参数一致。

dev 环境：

```bash
bash scripts/config-test.sh
echo "config-test exit code: $?"
```

test 环境：

```bash
CONFIG_PROFILE=test \
EXPECTED_USER_POOL=3 \
EXPECTED_TEXTBOOK_POOL=4 \
EXPECTED_USER_MARKER=nacos-user-test-v1 \
EXPECTED_TEXTBOOK_MARKER=nacos-textbook-test-v1 \
bash scripts/config-test.sh

echo "config-test exit code: $?"
```

成功时输出 ALL CONFIG READ CHECKS PASSED，退出码为0；
环境、标记、配置值或实际连接池值不匹配时返回1。

Maven 自动测试：

```bash
mvn -f services/user-service/pom.xml test
mvn -f services/textbook-service/pom.xml test
```
