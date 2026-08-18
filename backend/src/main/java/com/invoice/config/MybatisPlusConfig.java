package com.invoice.config;

// import com.baomidou.mybatisplus.annotation.DbType;
// import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
// import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 * 暂时注释掉以解决版本兼容性问题
 */
@Configuration
public class MybatisPlusConfig {
    
    // @Bean
    // public MybatisPlusInterceptor mybatisPlusInterceptor() {
    //     MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    //     interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    //     return interceptor;
    // }
}