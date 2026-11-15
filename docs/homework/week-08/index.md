# 第八周：服务间同步调用

### 设计与实现思路

选择“创建教材订单”作为本周的跨服务业务流程。订单服务接收客户端请求，
调用用户服务查询买家并检查启用状态，再调用教材服务查询教材、请求库存扣减，
最后保存订单。用户服务、教材服务和订单服务分别维护自己的数据库，
通过 HTTP 接口协作，避免跨服务直接访问数据库。

订单服务负责流程协调；教材服务负责在库存扣减的本地事务内，
检查教材上架状态和库存，并保存库存操作记录。
订单服务通过 requestId 识别重复订单，教材服务通过 orderNo 识别重复库存操作。

详细接口表和调用链路见
[服务间同步通信设计](../../service-communication.md)。

### 源码核对命令

在 PowerShell 中进入仓库根目录：

```powershell
Set-Location D:\microservices\microservices-practice-2412190107
```

查看下单流程：

```powershell
Get-Content services/order-service/src/main/java/com/zjgsu/zy/service/OrderService.java -Encoding utf8
```

查看调用方客户端：

```powershell
Get-Content services/order-service/src/main/java/com/zjgsu/zy/client/UserClient.java -Encoding utf8
Get-Content services/order-service/src/main/java/com/zjgsu/zy/client/TextbookClient.java -Encoding utf8
```

核对各服务接口路径：

```powershell
Get-ChildItem .\services -Recurse -Filter '*Controller.java' |
    Select-String -Pattern '@RequestMapping|@GetMapping|@PostMapping|@PutMapping'
```

### 核对结果

通过源码核对确认：

- 创建订单入口为 `POST /api/orders`。
- 订单服务调用用户服务的 `GET /api/users/{id}` 查询买家。
- 订单服务调用教材服务的 `GET /api/textbooks/{id}` 查询教材。
- 库存扣减接口为
  `PUT /internal/textbooks/{id}/stock-deductions/{orderNo}`。
- 远程校验及库存请求成功后，订单服务才保存本地订单。
- 当前调用方式为 RestTemplate；教材服务实例通过 Nacos 服务发现获取。
- 本步骤只完成流程选择与文档设计，未重新运行服务或执行业务测试。

### 已有验证记录

根据此前项目验证，订单创建、订单查询及库存变化已验证通过。
教材服务无可用实例时，下单返回 503；
教材服务恢复后重新发送下单请求，可以成功创建订单。
以上为此前验证结果，本周后续测试需另行记录。

### 关键结论

创建教材订单具有明确的跨服务协作需求，三个服务的数据边界和职责清晰。
后续将在该流程中引入 OpenFeign，并继续完成负载均衡验证和测试。
现有请求编号及库存操作记录可以减少重复订单和重复扣减，
但库存操作与订单保存仍属于不同的本地事务。
