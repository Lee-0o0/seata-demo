package org.example.orderservice.config;

import javax.sql.DataSource;
import org.apache.seata.saga.engine.StateMachineEngine;
import org.apache.seata.saga.engine.config.DbStateMachineConfig;
import org.apache.seata.saga.engine.impl.ProcessCtrlStateMachineEngine;
import org.apache.seata.saga.rm.StateMachineEngineHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
public class SagaConfiguration {
    @Bean
    @DependsOnDatabaseInitialization
    public DbStateMachineConfig sagaStateMachineConfig(DataSource dataSource,
            @Value("${spring.application.name}") String applicationId,
            @Value("${seata.tx-service-group}") String txServiceGroup) {
        var config = new DbStateMachineConfig();
        config.setDataSource(dataSource);
        config.setApplicationId(applicationId);
        config.setTxServiceGroup(txServiceGroup);
        config.setDefaultTenantId("order");
        config.setResources(new String[]{"classpath*:statelang/*.json"});
        config.setSagaJsonParser("jackson");
        config.setEnableAsync(false);
        config.setTransOperationTimeout(30000);
        config.setServiceInvokeTimeout(10000);
        // 写节点执行前注册分支，支持节点执行期间宕机后的 TC 恢复。
        config.setSagaBranchRegisterEnable(true);
        return config;
    }
    @Bean
    public StateMachineEngine stateMachineEngine(DbStateMachineConfig config) {
        var engine = new ProcessCtrlStateMachineEngine();
        engine.setStateMachineConfig(config);
        StateMachineEngineHolder.setStateMachineEngine(engine);
        // Seata 2.5 的兼容 SPI 可能选择 io.seata SagaResourceManager，两个入口共享同一配置和存储。
        var compatibleEngine = new io.seata.saga.engine.impl.ProcessCtrlStateMachineEngine();
        compatibleEngine.setStateMachineConfig(io.seata.saga.engine.impl.DefaultStateMachineConfig.wrap(config));
        io.seata.saga.rm.StateMachineEngineHolder.setStateMachineEngine(compatibleEngine);
        return engine;
    }
}
