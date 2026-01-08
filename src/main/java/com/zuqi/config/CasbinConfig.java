package com.zuqi.config;

import org.casbin.jcasbin.main.Enforcer;
import org.casbin.jcasbin.persist.Adapter;
import org.casbin.jcasbin.persist.file_adapter.FileAdapter;
import org.casbin.adapter.JDBCAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;

/**
 * Configuration for Casbin authorization.
 */
@Configuration
public class CasbinConfig {

    @Value("${casbin.model-path:casbin/model.conf}")
    private String modelPath;

    @Value("${casbin.policy-path:casbin/policy.csv}")
    private String policyPath;

    @Value("${casbin.use-database:false}")
    private boolean useDatabase;

    /**
     * Creates the Casbin Enforcer bean.
     * Uses file-based policy by default, can switch to database adapter.
     */
    @Bean
    public Enforcer enforcer(DataSource dataSource) throws Exception {
        // Load model from classpath
        ClassPathResource modelResource = new ClassPathResource(modelPath);
        String modelFilePath = modelResource.getFile().getAbsolutePath();

        Enforcer enforcer;

        if (useDatabase) {
            // Use JDBC adapter for database-stored policies
            Adapter adapter = new JDBCAdapter(dataSource);
            enforcer = new Enforcer(modelFilePath, adapter);
        } else {
            // Use file-based policy (good for development)
            ClassPathResource policyResource = new ClassPathResource(policyPath);
            String policyFilePath = policyResource.getFile().getAbsolutePath();
            enforcer = new Enforcer(modelFilePath, policyFilePath);
        }

        // Enable auto-save for policy changes
        enforcer.enableAutoSave(true);

        return enforcer;
    }
}
