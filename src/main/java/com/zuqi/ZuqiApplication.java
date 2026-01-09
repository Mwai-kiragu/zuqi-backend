package com.zuqi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication(exclude = {
        org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class
})
@EnableJpaAuditing
@EnableCaching
@EnableAsync
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class ZuqiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZuqiApplication.class, args);
    }
}
