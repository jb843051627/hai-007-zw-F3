package com.fc.v2.service.zw;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.service.impl.TZwSamTaskServiceImpl;
import com.fc.v2.service.impl.TZwStopBillServiceImpl;

/**
 * 测试专用 H2 内存库装配：真实 MyBatis-Plus Mapper 落真实 SQL，
 * 验「同一次计数/条件更新/事务回滚」这些必须落库才验得出的规矩。
 * 只点名本链路两个服务 Bean，不扫全套（避免 redis/quartz 等外围依赖进场）。
 */
@Configuration
@EnableTransactionManagement
@MapperScan(basePackages = "com.fc.v2.mapper.auto")
public class H2Db {

    @Bean(destroyMethod = "shutdown")
    public EmbeddedDatabase dataSource() {
        // MODE=MySQL 借 H2 名内参带入；建表脚本只保留本链路用到的列
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("zwtest;MODE=MySQL;DB_CLOSE_DELAY=-1")
                .addScript("classpath:sql/h2_zw.sql")
                .build();
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        return factory.getObject();
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public TZwStopBillServiceImpl zwStopBillService() {
        return new TZwStopBillServiceImpl();
    }

    @Bean
    public DispatchWindow dispatchWindow() {
        return new DispatchWindow();
    }

    @Bean
    public TZwSamTaskServiceImpl zwSamTaskService() {
        return new TZwSamTaskServiceImpl();
    }
}
