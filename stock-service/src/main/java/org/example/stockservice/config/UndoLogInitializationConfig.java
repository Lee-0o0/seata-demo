package org.example.stockservice.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Configuration(proxyBeanMethods = false)
public class UndoLogInitializationConfig {
    @Bean
    public static BeanPostProcessor undoLogInitializer() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if ("dataSource".equals(beanName) && bean instanceof DataSource dataSource) {
                    // Seata 2.5 checks undo_log before Boot's SQL initializer runs.
                    new ResourceDatabasePopulator(new ClassPathResource("undo-log.sql"))
                            .execute(dataSource);
                }
                return bean;
            }
        };
    }
}
