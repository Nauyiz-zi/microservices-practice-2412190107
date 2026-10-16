## 一、本周目标

本周在 Spring Boot 单体应用中实现校园二手教材交易平台的一条完整业务流程，包括业务模型、Service、REST API、数据库持久化和自动化测试。

核心流程为：

```text
创建教材
  ↓
查询在售教材
  ↓
创建购买订单
  ↓
检查库存和上架状态
  ↓
扣减库存并生成待支付订单
  ↓
查询订单
```

## 二、业务建模
### 设计思路
本周选择教材发布和教材购买作为核心业务流程。
主要实体包括：
- Textbook：表示卖家发布的二手教材。
- TradeOrder：表示买家购买教材形成的订单。

一个卖家可以发布多本教材，一本教材可以产生多个订单，一个订单只关联一本教材。
### 业务规则
订单创建时，Service 必须检查：
1. 教材是否存在；
2. 教材是否处于上架状态；
3. 购买数量是否大于 0；
4. 库存是否充足。

校验失败时不创建订单，也不扣减库存。

### 运行命令
```text
cd D:\microservices\microservices-practice-2412190107\monolith
.\mvnw.cmd test
```
### 运行结果
预期结果：
```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 三、单体应用实现
### 实现结构
```text
com.zjgsu.zy
├── controller
├── dto
├── model
├── repository
└── service
```

### REST 接口
|方法|路径|说明|
|---|---|---|
|POST|/api/textbooks|创建教材|
|GET|/api/textbooks|查询在售教材|
|GET|/api/textbooks/{id}|查询教材详情|
|POST|/api/orders|创建订单|
|GET|/api/orders/{id}|查询订单|

### 实现原则
Controller 只负责接收 HTTP 请求和返回 HTTP 响应。
业务规则、库存扣减和订单创建全部放在 Service 中。
数据访问通过 Spring Data JPA Repository 完成。

### 启动命令
```text
cd D:\microservices\microservices-practice-2412190107\monolith
.\mvnw.cmd spring-boot:run
```
