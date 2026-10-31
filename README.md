# Seata Demo

当前包含库存服务 `stock-service` 和订单模块 `order-service`。已实现下单、订单查询及跨服务库存扣减，通过 OpenFeign 调用库存服务，并通过 SCA 接入 Seata XA 分布式事务。

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

订单服务地址：`http://localhost:8082`。两个服务使用独立的 MySQL 容器和数据卷，分别管理库存与订单。

MySQL 默认连接信息：

| 服务 | Compose 服务 | 地址 | 数据库 | 账号 | 密码 |
| --- | --- | --- | --- | --- | --- |
| 库存 | stock-mysql | 127.0.0.1:3307 | stock_db | stock | stock_local |
| 订单 | order-mysql | 127.0.0.1:3308 | order_db | order_user | order_local |

这些默认密码仅用于本地演示。`docker compose up -d --wait` 会启动两个数据库和 Seata Server，并等待健康检查通过。已有库存数据保留；原库存库中的历史订单不会自动迁移，新订单写入 `order_db.purchase_order`。

## 数据库初始化

Docker 通过 `MYSQL_DATABASE` 创建数据库及应用账号，库存应用启动后按顺序执行其资源目录中的：

1. `schema.sql`：使用 `CREATE TABLE IF NOT EXISTS` 创建库存表。
2. `data.sql`：为不存在的演示商品 `1`、`2` 分别插入 100 库存。

`application.yaml` 中的 `spring.sql.init.mode: always` 启用 MySQL 启动初始化。重复启动不会重置已扣减的库存；SQL 执行失败会阻止应用启动。建表脚本不会自动修改已有表结构。

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

库存应用支持 `SERVER_PORT`、`DB_URL`、`DB_USERNAME`、`MYSQL_PASSWORD`；订单应用支持 `SERVER_PORT`、`ORDER_DB_URL`、`ORDER_DB_USERNAME`、`ORDER_MYSQL_PASSWORD`。Docker 库存库支持 `MYSQL_PORT`、`MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD`；订单库支持 `ORDER_MYSQL_PORT`、`ORDER_MYSQL_PASSWORD`、`ORDER_MYSQL_ROOT_PASSWORD`。修改数据库映射端口时，需要同步设置对应应用的 `DB_URL` 或 `ORDER_DB_URL`；已有数据卷的数据库密码不会因修改环境变量自动更新。

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
```

下单成功返回 `201` 和订单信息。参数错误返回 `400`、商品不存在返回 `404`、库存不足返回 `409`、库存服务异常返回 `502`、连接失败或超时返回 `503`。订单服务通过 `STOCK_SERVICE_URL` 配置库存服务地址，默认 `http://127.0.0.1:8081`，连接超时 3 秒、读取超时 5 秒。

订单服务启动时执行自己的 `schema.sql` 建表，不生成演示订单。请求先插入订单，再调用库存扣减，库存拒绝或调用异常会触发全局回滚。

### 当前事务边界

两个服务均启用 `seata.data-source-proxy-mode: XA`，订单入口使用 `@GlobalTransactional`，并保留各自的本地 `@Transactional`。SCA 自动通过 Feign 传播 XID，MySQL 的 XA 分支由 Seata 统一提交或回滚；XA 模式不需要 `undo_log` 表。

Seata 默认使用 `127.0.0.1:8091`，通过 `SEATA_SERVER_ADDR` 可覆盖。事务分组为 `seata_demo_tx_group`，使用 file 注册和配置方式直连 TC。服务端配置位于 `seata-server/application.yml`，TC 会话通过 `seata-data` 数据卷持久化。Docker 将 TC 端口发布到 `127.0.0.1:8091`，供宿主机上的两个 Java 应用连接。当前 Compose 面向两个 Java 应用运行在宿主机的本地演示场景。

XA 分支在全局事务完成前持有数据库锁，全局事务超时为 30 秒。接口暂不具备幂等性，不应自动重试结果未知的下单或扣减请求。

当前固定 Connector/J 为 `8.4.0`，避免 Seata 2.5.0 的 XA 转换逻辑不支持 9.x 驱动。Docker 使用 `apache/seata-server:2.5.0`，与 SCA 引入的客户端版本保持一致；已通过容器健康检查和 XA 提交、回滚联调。

数据库、TC 和库存应用启动后，可运行 `python3 scripts/verify-xa.py` 验证。脚本会临时启动订单应用（18082 端口），测试成功提交、库存不足、商品不存在，以及库存 prepare 后响应失败的全局回滚；验证结束后停止临时订单应用。成功用例会保留一笔演示订单并扣减商品 2 的一件库存。
