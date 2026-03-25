package com.zuqi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.frontend")
@Getter
@Setter
public class AppConfig {

    private String url = "https://zuqi.pestoe.com";
}
