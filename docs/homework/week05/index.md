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
