# Spring Boot 单体应用容器化

## 一、本周目标

本周将上周完成的 Spring Boot 单体应用与 MySQL 数据库容器化，实现：

- Docker 多阶段镜像构建；
- Docker Compose 一键启动；
- 业务 API 创建和查询；
- MySQL 数据持久化；
- 删除并重建容器后数据仍然存在。

本周仍然使用原有仓库中的 `monolith/` 工程。

## 二、Dockerfile 与镜像构建

### 2.1 Dockerfile

`monolith/Dockerfile` 使用多阶段构建：

1. 第一阶段使用 Maven 和 JDK 25 编译、测试并打包；
2. 第二阶段使用 Eclipse Temurin JRE 25 运行打包后的 JAR；
3. 运行镜像中不包含 Maven、源代码和本地构建缓存。

`.dockerignore` 排除了：

- `target/`
- `.idea/`
- `.vscode/`
- `.git/`
- 本地日志；
- `.env`；
- 本地密码和密钥文件。

### 2.2 镜像构建命令

在仓库根目录执行：

```powershell
cd D:\microservices\microservices-practice-2412190107
docker build -t campus-textbook:week05 ./monolith
```
### 2.3 镜像名称和结果
镜像名称：
```text
campus-textbook:week05
```
构建结果：
```text
Successfully tagged campus-textbook:week05
```
检查镜像：
```text
docker image ls campus-textbook
```
实际结果：
```text
IMAGE                    ID             DISK USAGE   CONTENT SIZE   EXTRA                                                                                          
campus-textbook:week05   ba3b7076cc4a        585MB          169MB    
```
检查镜像中的可运行 JAR：
```text
docker run --rm --entrypoint sh campus-textbook:week05 -c "ls -lh /app/app.jar; java -version"
```
实际结果：
```text
-rw-r--r-- 1 10001 10001 56M Oct 22 03:45 /app/app.jar
openjdk version "25.0.4.1" 2026-08-18 LTS                                                                                                                          
OpenJDK Runtime Environment Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS)
OpenJDK 64-Bit Server VM Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS, mixed mode, sharing)
```

说明：该镜像能够在没有本机 Maven 和 Java 参与构建的情况下完成编译和运行。


## 三、Compose 编排配置与启动

### 3.1 编排配置

根目录中的 compose.yaml 定义了两个服务：
- mysql：使用 mysql:8.4.11 镜像；
- app：从 ./monolith 构建 Spring Boot 应用。
两个服务加入同一个 campus_net 网络。

应用通过下面的地址连接数据库：
```text
jdbc:mysql://mysql:3306/campus_textbook
```
这里的 mysql 是 Compose 中的服务名，不是 localhost。

MySQL 使用命名数据卷：
```text
volumes:
  - mysql_data:/var/lib/mysql
```

MySQL 配置了健康检查，应用通过以下配置等待 MySQL 健康后再启动：
```text
depends_on:
  mysql:
    condition: service_healthy
```

### 3.2 环境变量

复制环境变量模板：
```text
Copy-Item .env.example .env
```

在 .env 中配置本地使用的数据库密码：
```text
APP_PORT=8080
MYSQL_PORT=3307
DB_NAME=campus_textbook
DB_USER=campus_app
DB_PASSWORD=<实际应用数据库密码>
DB_ROOT_PASSWORD=<实际root密码>
```
.env 只用于本地运行，不提交到 GitHub；.env.example 用于说明配置格式，可以提交。

### 3.3 编排配置检查
```text
docker compose config --quiet
```

实际结果：
命令无错误输出，Compose 配置检查通过

### 3.4 启动命令
```text
docker compose up -d --build
```

查看服务状态：
```text
docker compose ps
```
实际结果：
```text
NAME                             IMAGE                    COMMAND                   SERVICE   CREATED          STATUS                    PORTS
campus-textbook-week05-app-1     campus-textbook:week05   "java -jar /app/app.…"   app       14 seconds ago   Up 8 seconds              127.0.0.1:8080->8080/tcp
campus-textbook-week05-mysql-1   mysql:8.4.11             "docker-entrypoint.s…"   mysql     14 seconds ago   Up 14 seconds (healthy)   127.0.0.1:3307->3306/tcp
```

预期状态：
- mysql：healthy
- app：running

查看应用日志：
```text
docker compose logs --tail=100 app
```

实际结果：
日志出现 Started MonolithApplication

## 四、业务 API 验证

本次使用 Postman 通过容器映射端口访问应用。

应用访问地址：
```text
http://localhost:8080
```

### 4.1 健康接口

请求：
```text
GET http://localhost:8080/actuator/health
```

实际响应状态：
```text
200 OK
```

实际响应：
```text
{
    "groups": [
        "liveness",
        "readiness"
    ],
    "status": "UP"
}
```

### 4.2 创建教材

请求：
```text
POST http://localhost:8080/api/textbooks
```

Body 选择：
```text
raw → JSON
```

请求内容：
```text
{
  "title": "计算机组成原理",
  "isbn": "9787300000502",
  "author": "唐朔飞",
  "publisher": "高等教育出版社",
  "conditionDescription": "九成新",
  "price": 25.50,
  "stock": 3,
  "sellerId": 1001
}
```

实际响应状态：
```text
201 Created
```

实际返回的教材 ID：
```text
4
```

实际响应关键字段：
```text
{
    "id": 4,
    "title": "计算机组成原理",
    "isbn": "9787300000502",
    "author": "唐朔飞",
    "publisher": "高等教育出版社",
    "conditionDescription": "九成新",
    "price": 25.50,
    "stock": 3,
    "sellerId": 1001,
    "listed": true,
    "createdAt": "2026-10-23T09:47:52.65173168"
}
```

### 4.3 查询教材

使用创建接口返回的实际 ID：
```text
GET http://localhost:8080/api/textbooks/4
```

实际响应状态：
```text
200 OK
```

实际查询结果：
```text
{
    "id": 4,
    "title": "计算机组成原理",
    "isbn": "9787300000502",
    "author": "唐朔飞",
    "publisher": "高等教育出版社",
    "conditionDescription": "九成新",
    "price": 25.50,
    "stock": 3,
    "sellerId": 1001,
    "listed": true,
    "createdAt": "2026-10-23T09:47:52.651732"
}
```

查询结果中的 ID、书名、ISBN、价格和库存与创建接口返回的数据一致。

## 五、数据持久化验证

### 5.1 创建并记录数据

首先通过 Postman 创建教材，记录返回的教材 ID：
```text
教材ID：4
书名：计算机组成原理
ISBN：9787300000502
价格：25.50
库存：3
卖家ID：1001
```

### 5.2 删除容器

在仓库根目录执行：
```text
docker compose down
```

本次操作没有使用：
```text
docker compose down -v
```

因此只删除了容器和网络，没有删除 MySQL 命名数据卷。

### 5.3 检查数据卷
```text
docker volume ls
```
实际结果：
campus-textbook-week05_mysql_data 仍然存在

### 5.4 重新启动容器
```text
docker compose up -d
```

检查状态：
```text
docker compose ps
```

等待 mysql 显示 healthy，并确认 app 正常运行。

再次检查应用健康状态：
```text
GET http://localhost:8080/actuator/health
```

实际结果：
```text
200 OK，status 为 UP
```

### 5.5 再次查询原记录

在 Postman 中重新发送：
```text
GET http://localhost:8080/api/textbooks/4
```

实际响应：
```text
 {
    "id": 4,
    "title": "计算机组成原理",
    "isbn": "9787300000502",
    "author": "唐朔飞",
    "publisher": "高等教育出版社",
    "conditionDescription": "九成新",
    "price": 25.50,
    "stock": 3,
    "sellerId": 1001,
    "listed": true,
    "createdAt": "2026-10-23T09:47:52.651732"
}
```

重建容器前后对比：
字段	重建前	重建后
id	4	4
title	计算机组成原理 计算机组成原理
isbn	9787300000502	9787300000502
price	25.50	25.50
stock	3	3
sellerId	1001	1001


验证结论：
```text
容器删除并重新创建后，教材记录仍然可以通过原 ID 查询，
ID 和书名、ISBN、价格、库存等关键字段均保持不变，
说明 MySQL 数据持久化验证成功。
```
