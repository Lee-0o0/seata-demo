# Seata Demo

当前包含库存、订单和账户三个服务。订单与库存使用 Seata AT，账户使用 TCC；订单通过 OpenFeign 调用库存扣减和账户冻结，由同一全局事务协调提交或回滚。

## 启动

需要 JDK 17 或以上、Maven 和 Docker。以下命令均在 `seata-demo` 目录执行。

```bash
docker compose up -d --wait
mvn clean package -DskipTests
java -jar stock-service/target/stock-service-0.0.1-SNAPSHOT.jar
```

库存服务地址：`http://localhost:8081`。另开终端启动订单服务：

```bash
java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar
```

订单服务地址：`http://localhost:8082`。另开终端启动账户服务：

```bash
java -jar account-service/target/account-service-0.0.1-SNAPSHOT.jar
```

账户服务地址：`http://localhost:8083`。下单前需启动三个服务，各自使用独立的 MySQL 容器和数据卷。

MySQL 默认连接信息：

| 服务 | Compose 服务 | 地址 | 数据库 | 账号 | 密码 |
| --- | --- | --- | --- | --- | --- |
| 库存 | stock-mysql | 127.0.0.1:3307 | stock_db | stock | stock_local |
| 订单 | order-mysql | 127.0.0.1:3308 | order_db | order_user | order_local |
| 账户 | account-mysql | 127.0.0.1:3309 | account_db | account_user | account_local |

这些默认密码仅用于本地演示。`docker compose up -d --wait` 会启动三个数据库和 Seata Server，并等待健康检查通过。已有库存数据保留；原库存库中的历史订单不会自动迁移，新订单写入 `order_db.purchase_order`。

## 数据库初始化

Docker 通过 `MYSQL_DATABASE` 创建数据库及应用账号，库存应用启动后按顺序执行其资源目录中的：

1. `schema.sql`：使用 `CREATE TABLE IF NOT EXISTS` 创建库存表。
2. `data.sql`：为不存在的演示商品 `1`、`2` 分别插入 100 库存。

`application.yaml` 中的 `spring.sql.init.mode: always` 启用 MySQL 启动初始化。重复启动不会重置已扣减的库存；SQL 执行失败会阻止应用启动。订单初始化脚本会为已有订单表幂等补充 `unit_price`、`total_amount` 列，历史订单价格保持 NULL。订单库的 `product` 表初始化商品 1（10 元）、2（20 元）；订单保存成交单价和总金额快照。账户初始化用户 1（10000 元）和用户 2（1 元），重启不会重置余额。

Docker 使用命名卷持久化数据，停止容器不会清空数据库。已有 Docker 数据卷可继续使用，无需重建。

## 接口示例

查询库存：

```bash
curl http://localhost:8081/stocks/1
```

扣减库存：

```bash
curl -i -X POST http://localhost:8081/stocks/deduct \
  -H 'Content-Type: application/json' \
  -d '{"productId":1,"quantity":10}'
```

成功返回 `204`；参数错误返回 `400`，商品不存在返回 `404`，库存不足返回 `409`。扣减使用带库存条件的原子更新，防止并发扣成负数。

## 配置与停止

库存应用支持 `SERVER_PORT`、`DB_URL`、`DB_USERNAME`、`MYSQL_PASSWORD`；订单应用支持 `SERVER_PORT`、`ORDER_DB_URL`、`ORDER_DB_USERNAME`、`ORDER_MYSQL_PASSWORD`。账户应用支持 `SERVER_PORT`、`ACCOUNT_DB_URL`、`ACCOUNT_DB_USERNAME`、`ACCOUNT_MYSQL_PASSWORD`，账户数据库端口由 `ACCOUNT_MYSQL_PORT` 配置（默认 3309）。订单通过 `ACCOUNT_SERVICE_URL` 配置账户地址（默认 `http://127.0.0.1:8083`），连接超时 3 秒、读取超时 5 秒。Docker 库存库支持 `MYSQL_PORT`、`MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD`；订单库支持 `ORDER_MYSQL_PORT`、`ORDER_MYSQL_PASSWORD`、`ORDER_MYSQL_ROOT_PASSWORD`。修改数据库映射端口时，需要同步设置对应应用的 `DB_URL` 或 `ORDER_DB_URL`；已有数据卷的数据库密码不会因修改环境变量自动更新。

应用在前台运行时按 `Ctrl+C` 停止。停止基础服务并保留数据：

```bash
docker compose down
```

## 下单与查询订单

```bash
curl -i -X POST http://localhost:8082/orders \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"productId":1,"quantity":10}'

# 将 1 替换为下单响应中的订单 ID
curl http://localhost:8082/orders/1
curl http://localhost:8081/stocks/1
curl http://localhost:8083/accounts/1
```

下单成功返回 `201` 和订单信息。参数错误返回 `400`、商品不存在返回 `404`、库存或余额不足返回 `409`、下游服务异常返回 `502`、连接失败或超时返回 `503`。订单服务通过 `STOCK_SERVICE_URL` 配置库存服务地址，默认 `http://127.0.0.1:8081`，连接超时 3 秒、读取超时 5 秒。

订单服务启动时执行自己的 `schema.sql` 建表，不生成演示订单。请求先查商品价格并插入订单，再调用库存扣减、账户冻结；任一调用异常会触发全局回滚。传入 `"exception":true` 可在冻结成功后模拟异常，验证库存回滚及账户解冻。

### 当前事务边界

订单和库存服务均启用 `seata.data-source-proxy-mode: AT`，订单入口使用 `@GlobalTransactional`，并保留各自的本地 `@Transactional`。SCA 自动通过 Feign 传播 XID，AT 一阶段提交业务数据和回滚日志，二阶段由 Seata 决定完成提交或根据回滚日志补偿。两个业务库均在数据源代理创建前执行各自的 `undo-log.sql`，幂等创建 `undo_log` 表，重启应用即可补齐，无需清空数据库。

Seata 默认使用 `127.0.0.1:8091`，通过 `SEATA_SERVER_ADDR` 可覆盖。事务分组为 `seata_demo_tx_group`，使用 file 注册和配置方式直连 TC。服务端配置位于 `seata-server/application.yml`，TC 会话通过 `seata-data` 数据卷持久化。Docker 将 TC 端口发布到 `127.0.0.1:8091`，供宿主机上的三个 Java 应用连接。当前 Compose 面向三个 Java 应用运行在宿主机的本地演示场景。

账户服务关闭 AT 数据源代理，通过 `@LocalTCC` 和 `@TwoPhaseBusinessAction(useTCCFence=true)` 注册账户分支：Try 将可用余额转为冻结余额并写入 `account_freeze`；Confirm 消耗冻结余额；Cancel 将冻结余额退回可用余额。每个阶段的余额、明细及 `tcc_fence_log` 状态在同一本地事务内变更。冻结明细按 XID/branchId 隔离，Fence 处理重复回调、空回滚和防悬挂。账户扣款入口要求携带全局 XID，不开放 Confirm/Cancel HTTP 接口。

AT 一阶段提交后释放数据库本地锁，并通过 Seata 全局锁协调写入；全局事务超时为 30 秒。接口暂不具备幂等性，不应自动重试结果未知的下单或扣减请求。

当前固定 Connector/J 为 `8.4.0`，沿用此前已验证的驱动版本。Docker 使用 `apache/seata-server:2.5.0`，与 SCA 引入的客户端版本保持一致；已通过容器健康检查和 AT 提交、回滚联调。

构建后运行 `python3 scripts/verify-tcc.py` 验证 AT + TCC。脚本临时启动三个应用（18081/18082/18083），验证成功提交、异常取消、冻结后响应失败补偿、余额不足、XID 传播、并发一致性、重复 Confirm/Cancel、空回滚及防悬挂。测试会创建演示订单并消耗用户 1 的余额及商品 2 库存，还会创建独立的并发测试账户；不清空已有数据。测试结束后停止临时应用。库存 AT 全局锁竞争可能使部分并发请求失败，脚本核对失败请求不会遗留扣款或库存扣减。
