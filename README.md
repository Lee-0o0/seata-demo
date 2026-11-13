# Seata Saga 演示

三个服务演示编排式 Saga：订单服务启动状态机，依次扣库存、扣余额；后续失败时逆序补偿。库存和账户各自提交本地事务，不依赖 Seata。订单服务使用 Seata 2.5.0 状态机引擎及 Server 完成流程持久化和恢复。

## 从零启动

需要 JDK 17 或以上、Maven、Docker；以下命令在 `seata-demo` 目录执行。

```bash
docker compose up -d --wait
mvn clean package -DskipTests
```

分别在三个终端启动应用：

```bash
java -jar stock-service/target/stock-service-0.0.1-SNAPSHOT.jar
java -jar account-service/target/account-service-0.0.1-SNAPSHOT.jar
java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar
```

| 应用 | HTTP 端口 | 数据库地址 | 数据库 | 账号 / 密码 |
| --- | --- | --- | --- | --- |
| 库存 | 8081 | 127.0.0.1:3307 | stock_db | stock / stock_local |
| 订单 | 8082 | 127.0.0.1:3308 | order_db | order_user / order_local |
| 账户 | 8083 | 127.0.0.1:3309 | account_db | account_user / account_local |

Compose 启动三个 MySQL 容器和 Seata Server（127.0.0.1:8091），Java 应用在宿主机运行。连接配置见各应用的 `application.yaml`；默认密码仅供本地演示。

应用启动时执行 `schema.sql` 和 `data.sql`，订单服务额外执行 `saga-schema.sql`。全新环境初始化商品 1（10 元）、2（20 元），各有 100 库存；用户 1 有 10000 元，用户 2 有 1 元。初始没有订单和参与者操作流水。初始化只插入缺失的演示数据，普通重启不会恢复已消耗的库存或余额。表结构直接定义完整字段，不提供旧表迁移。

## 演示执行与补偿

```bash
# 成功下单：201，订单状态 SUCCESS
curl -i -X POST http://localhost:8082/orders \
  -H 'Content-Type: application/json' \
  -d '{"requestId":"demo-success-1","userId":1,"productId":1,"quantity":1}'

# 余额不足：409，库存补偿完成后订单状态 CLOSED
curl -i -X POST http://localhost:8082/orders \
  -H 'Content-Type: application/json' \
  -d '{"requestId":"demo-failure-1","userId":2,"productId":1,"quantity":1}'

# 两次扣减后模拟异常：验证退款及库存归还
curl -i -X POST http://localhost:8082/orders \
  -H 'Content-Type: application/json' \
  -d '{"requestId":"demo-compensation-1","userId":1,"productId":1,"quantity":1,"exception":true}'

# 将 1 替换为下单响应中的订单 ID
curl http://localhost:8082/orders/1
curl http://localhost:8081/stocks/1
curl http://localhost:8083/accounts/1
```

下单重试复用相同 `requestId`；参数不同则返回 409。不传时服务端生成标识，但无法据此识别客户端的重复下单。补偿尚未完成时返回 202，通过订单查询等待最终结果。更多请求见 `scripts/requests.http`。

## 代码入口

- `OrderService`：保存订单、幂等下单、启动状态机、同步业务状态。
- `statelang/order-saga.json`：扣库存 → 扣余额 → 检查模拟异常 → 成功；异常进入补偿。
- `OrderSagaActions`：调用库存、账户扣减及补偿，使用稳定的操作标识。
- `SagaConfiguration`：配置引擎、持久化及 Server 恢复入口。
- `saga-schema.sql`：依据 Seata 2.5.0 存储源码整理的三张引擎表，非直接复制的官方原版脚本。

订单先独立提交，远程操作分别提交；补偿是新的业务事务，失败订单保留为 CLOSED。参与者通过本地操作流水实现幂等、空补偿和防悬挂。Server 推动已启动流程的超时恢复及补偿重试；订单定时任务补启动尚无流程实例的订单，并同步最终状态。Saga 允许中间结果可见，不提供全局隔离。

## 验证

构建并启动 Compose 基础服务后执行，验证脚本自行启动并停止临时 Java 应用：

```bash
python3 scripts/verify-saga-participants.py
python3 scripts/verify-saga-orders.py
```

验证覆盖重复及并发扣减、空补偿、防悬挂、成功下单、余额不足、响应失败、补偿重试和订单服务重启恢复。临时端口使用 18081/18082/18083 和 19081/19083；测试创建独立测试数据并保留在数据库，恢复空白演示环境需执行以下重置。

## 停止与完全重置

先停止 Java 应用。普通停止保留数据：

```bash
docker compose down
```

**以下操作会清空本项目三个数据库及 Seata 会话的全部数据卷：**

```bash
docker compose down -v
docker compose up -d --wait
```

重启三个 Java 应用后，自动建表并恢复初始演示数据。
