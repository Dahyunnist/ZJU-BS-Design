package com.zju.imagemanger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 允许本机及局域网/手机访问（含微信内置浏览器），避免跨域403
                .allowedOrigins(
                        "http://localhost:3000",
                        "http://localhost:5173",
                        "http://10.193.73.98:3000",
                        "http://10.193.73.98",
                        "http://127.0.0.1:3000"
                )
                .allowedOriginPatterns("*") // 放宽为所有来源，便于移动端/内网访问
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}

