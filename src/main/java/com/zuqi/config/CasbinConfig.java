package com.zuqi.config;

import org.casbin.jcasbin.main.Enforcer;
import org.casbin.jcasbin.model.Model;
import org.casbin.jcasbin.persist.Adapter;
import org.casbin.jcasbin.persist.file_adapter.FileAdapter;
import org.casbin.adapter.JDBCAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class CasbinConfig {

    @Value("${casbin.model-path:casbin/model.conf}")
    private String modelPath;

    @Value("${casbin.policy-path:casbin/policy.csv}")
    private String policyPath;

    @Value("${casbin.use-database:false}")
    private boolean useDatabase;

    @Bean
    public Enforcer enforcer(DataSource dataSource) throws Exception {
        // Load model from classpath as InputStream (works inside JARs/WARs)
        ClassPathResource modelResource = new ClassPathResource(modelPath);
        Model model = new Model();
        try (InputStream is = modelResource.getInputStream()) {
            String modelText = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            model.loadModelFromText(modelText);
        }

        Enforcer enforcer;

        if (useDatabase) {
            // Use JDBC adapter for database-stored policies
            Adapter adapter = new JDBCAdapter(dataSource);
            enforcer = new Enforcer(model, adapter);
        } else {
            // Use file-based policy (good for development)
            // For file-based, we need to copy to a temp file since FileAdapter needs a path
            ClassPathResource policyResource = new ClassPathResource(policyPath);
            java.io.File tempPolicy = java.io.File.createTempFile("casbin-policy", ".csv");
            tempPolicy.deleteOnExit();
            try (InputStream is = policyResource.getInputStream();
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(tempPolicy)) {
                StreamUtils.copy(is, fos);
            }
            Adapter adapter = new FileAdapter(tempPolicy.getAbsolutePath());
            enforcer = new Enforcer(model, adapter);
        }

        // Enable auto-save for policy changes
        enforcer.enableAutoSave(true);

        return enforcer;
    }
}
