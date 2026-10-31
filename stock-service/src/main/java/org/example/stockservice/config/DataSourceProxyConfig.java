package org.example.stockservice.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.seata.rm.datasource.DataSourceProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * @author lhb
 * @date 2026/10/31
 */
// @Configuration
public class DataSourceProxyConfig {

    @Bean("dataSourceProxy")
    public DataSource dataSource(HikariDataSource hikariDataSource) {
        // 手动代理示例；启用前需关闭自动代理，并显式配置原始数据源及 @Primary。
        return new DataSourceProxy(hikariDataSource);
    }

}
