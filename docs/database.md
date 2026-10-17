# 数据库设计

## 1. 数据库说明

本项目使用 MySQL 作为本地开发数据库，数据库名称为：

```text
campus_textbook
```

应用通过 Spring Data JPA 访问数据库，开发阶段使用：

```text
spring.jpa.hibernate.ddl-auto: update
```

自动化测试使用 H2 内存数据库。

## 2. textbooks 教材表

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | 主键、自增 | 教材 ID |
| title | VARCHAR(120) | 非空 | 书名 |
| isbn | VARCHAR(30) | 非空 | ISBN |
| author | VARCHAR(80) | 非空 | 作者 |
| publisher | VARCHAR(120) | 可空 | 出版社 |
| condition_description | VARCHAR(200) | 非空 | 教材成色 |
| price | DECIMAL(10,2) | 非空 | 售价 |
| stock | INT | 非空 | 可售库存 |
| seller_id | BIGINT | 非空 | 卖家 ID |
| listed | BOOLEAN | 非空 | 是否上架 |
| created_at | DATETIME | 非空 | 创建时间 |
| updated_at | DATETIME | 非空 | 修改时间 |

约束：
- id 为主键；
- price 必须大于 0；
- stock 不能小于 0；
- isbn + seller_id 具有唯一约束；
- listed = true 才允许创建订单。

## 3. trade_orders 订单表

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | 主键、自增 | 订单 ID |
| order_no | VARCHAR(40) | 非空、唯一 | 订单编号 |
| buyer_id | BIGINT | 非空 | 买家 ID |
| seller_id | BIGINT | 非空 | 卖家 ID |
| textbook_id | BIGINT | 外键、非空 | 教材 ID |
| textbook_title | VARCHAR(120) | 非空 | 教材名称快照 |
| quantity | INT | 非空 | 购买数量 |
| unit_price | DECIMAL(10,2) | 非空 | 下单时单价 |
| total_amount | DECIMAL(10,2) | 非空 | 订单总金额 |
| status | VARCHAR(30) | 非空 | 订单状态 |
| created_at | DATETIME | 非空 | 创建时间 |

约束：
- id 为主键；
- order_no 唯一；
- textbook_id 关联 textbooks.id；
- quantity 必须大于 0；
- total_amount = unit_price × quantity；
- 新订单状态为 PENDING_PAYMENT。

## 4. 表关系

```text
textbooks 1 -------- N trade_orders
```
一本教材可以被多个买家分别下单，因此教材表和订单表是一对多关系。
订单创建时保存教材名称和单价快照，防止教材后续修改后影响历史订单。

## 5. 测试数据准备

本项目不使用固定的生产环境 data.sql，避免每次启动 MySQL 时重复插入相同数据。
自动化测试通过测试类的 @BeforeEach：
1. 删除已有订单；
2. 删除已有教材；
3. 创建新的测试教材；
4. 执行 Repository、Service 和 API 测试。
因此执行以下命令可以重复通过：

```text
cd monolith
./mvnw test
```
