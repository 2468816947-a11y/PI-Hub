package com.patrol.platform.config;

import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import jakarta.servlet.MultipartConfigElement;

/**
 * 文件上传配置(接口文档 §2.7.1: 单文件 ≤ 20MB)。
 * <p>
 * Nginx 同时配置 client_max_body_size 20m, 双重限制保证接口不被大文件拖垮。
 */
@Configuration
public class MultipartConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(20));
        factory.setMaxRequestSize(DataSize.ofMegabytes(25));
        return factory.createMultipartConfig();
    }
}
