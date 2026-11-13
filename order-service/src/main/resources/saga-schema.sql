-- Columns match Seata 2.5.0 StateLangStoreSqls / StateLogStoreSqls.
CREATE TABLE IF NOT EXISTS seata_state_machine_def (
    id VARCHAR(128) NOT NULL COMMENT '状态机定义 ID，唯一标识一个流程定义版本' PRIMARY KEY,
    name VARCHAR(128) NOT NULL COMMENT '状态机名称，对应流程 JSON 的 Name',
    tenant_id VARCHAR(32) NOT NULL COMMENT '租户标识，用于隔离流程定义',
    app_name VARCHAR(32) COMMENT '所属应用名称',
    type VARCHAR(20) COMMENT '状态机定义类型',
    comment_ VARCHAR(255) COMMENT '状态机定义的描述信息',
    ver VARCHAR(16) NOT NULL COMMENT '状态机定义版本，对应流程 JSON 的 Version',
    gmt_create DATETIME(3) NOT NULL COMMENT '定义创建时间，精确到毫秒',
    status VARCHAR(2) NOT NULL COMMENT '状态机定义的启用状态',
    content LONGTEXT COMMENT '状态机定义 JSON，包含节点、路由和补偿配置',
    recover_strategy VARCHAR(16) COMMENT '故障恢复策略：Compensate 补偿，Forward 向前重试',
    UNIQUE KEY uk_saga_definition (name, tenant_id, ver)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Saga 状态机定义：保存流程 JSON 及其版本';
CREATE TABLE IF NOT EXISTS seata_state_machine_inst (
    id VARCHAR(128) NOT NULL COMMENT '流程实例 ID；当前接入 Seata Server 时使用全局事务 XID' PRIMARY KEY,
    machine_id VARCHAR(128) NOT NULL COMMENT '关联的状态机定义 ID，对应 seata_state_machine_def.id',
    tenant_id VARCHAR(32) NOT NULL COMMENT '租户标识，与业务标识共同限定流程实例唯一性',
    parent_id VARCHAR(128) COMMENT '父流程实例 ID，用于子状态机，顶层流程为空',
    business_key VARCHAR(128) COMMENT '业务标识，订单流程使用 order: 加请求 ID 的 SHA-256 摘要',
    gmt_started DATETIME(3) NOT NULL COMMENT '流程开始时间，精确到毫秒',
    gmt_end DATETIME(3) COMMENT '流程结束时间，未结束时为空',
    gmt_updated DATETIME(3) COMMENT '流程更新时间，也用于乐观并发控制',
    status VARCHAR(2) COMMENT '正向执行状态：RU 执行中、SU 成功、FA 失败、UN 未知',
    compensation_status VARCHAR(2) COMMENT '补偿执行状态：RU 执行中、SU 成功、FA 失败、UN 未知；未触发时为空',
    is_running TINYINT COMMENT '流程是否正在执行：1 是，0 否',
    start_params LONGTEXT COMMENT 'JSON 格式的流程启动参数',
    end_params LONGTEXT COMMENT 'JSON 格式的流程结束参数',
    excep LONGBLOB COMMENT 'Java 序列化的流程异常，使用二进制存储',
    UNIQUE KEY uk_saga_business (business_key, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Saga 状态机执行实例：记录一次业务流程的执行及补偿状态';
CREATE TABLE IF NOT EXISTS seata_state_inst (
    id VARCHAR(128) NOT NULL COMMENT '节点执行实例 ID，与流程实例 ID 组成主键',
    machine_inst_id VARCHAR(128) NOT NULL COMMENT '所属流程实例 ID，对应 seata_state_machine_inst.id',
    name VARCHAR(128) NOT NULL COMMENT '节点名称，对应流程 JSON 中 States 的键',
    type VARCHAR(20) COMMENT '节点类型，例如 ServiceTask、Choice',
    service_name VARCHAR(128) COMMENT '调用的服务名称，Spring Bean 调用时为 Bean 名称',
    service_method VARCHAR(128) COMMENT '调用的服务方法名称',
    service_type VARCHAR(16) COMMENT '服务调用类型，例如 Spring Bean 调用',
    business_key VARCHAR(128) COMMENT '节点执行的业务标识',
    state_id_compensated_for VARCHAR(128) COMMENT '当前补偿节点所对应的原节点执行实例 ID',
    state_id_retried_for VARCHAR(128) COMMENT '当前重试节点所对应的原节点执行实例 ID',
    gmt_started DATETIME(3) NOT NULL COMMENT '节点开始时间，精确到毫秒',
    gmt_end DATETIME(3) COMMENT '节点结束时间，未结束时为空',
    gmt_updated DATETIME(3) COMMENT '节点执行记录的更新时间，精确到毫秒',
    is_for_update TINYINT COMMENT '是否为更新型业务节点：1 是，0 否；不表示 SQL 行锁',
    input_params LONGTEXT COMMENT 'JSON 格式的节点调用入参',
    output_params LONGTEXT COMMENT 'JSON 格式的节点调用返回结果',
    status VARCHAR(2) COMMENT '节点执行状态：RU 执行中、SU 成功、FA 失败、UN 未知',
    excep LONGBLOB COMMENT 'Java 序列化的节点异常，使用二进制存储',
    PRIMARY KEY (id, machine_inst_id),
    KEY idx_saga_machine (machine_inst_id, gmt_started)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Saga 节点执行实例：记录节点调用、执行结果及补偿重试关联';
