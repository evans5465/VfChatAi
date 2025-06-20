package com.example.VF_ChatAi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import jakarta.validation.Validator;
import java.util.List;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
@EnableCaching
@EnableTransactionManagement
@EnableAspectJAutoProxy
public class AppConfiguration implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS,PATCH}")
    private String allowedMethods;

    @Value("${app.cors.allowed-headers:*}")
    private String allowedHeaders;

    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${app.description:AI-Powered Chat Application}")
    private String appDescription;

    /**
     * CORS Configuration
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.split(","))
                .allowedMethods(allowedMethods.split(","))
                .allowedHeaders(allowedHeaders.split(","))
                .allowCredentials(allowCredentials)
                .maxAge(3600);
    }

    /**
     * OpenAPI/Swagger Configuration
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("VFChatAI API")
                        .version(appVersion)
                        .description(appDescription)
                        .contact(new Contact()
                                .name("VFChatAI Support")
                                .email("support@vfchatai.com")
                                .url("https://vfchatai.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Development server"),
                        new Server().url("https://api.vfchatai.com").description("Production server")
                ));
    }

    /**
     * Async Task Executor Configuration
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("VFChatAI-Async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Email Task Executor (separate thread pool for email operations)
     */
    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("VFChatAI-Email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * SMS Task Executor (separate thread pool for SMS operations)
     */
    @Bean(name = "smsTaskExecutor")
    public Executor smsTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(30);
        executor.setThreadNamePrefix("VFChatAI-SMS-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Bean Validation Configuration
     */
    @Bean
    public Validator validator() {
        return new LocalValidatorFactoryBean();
    }

    /**
     * Application Health Indicator
     */
    @Bean
    public ApplicationHealthIndicator applicationHealthIndicator() {
        return new ApplicationHealthIndicator();
    }

    /**
     * Custom Health Indicator Class
     */
    public static class ApplicationHealthIndicator {

        public HealthStatus getHealthStatus() {
            // Perform health checks here
            return new HealthStatus(true, "Application is running normally", System.currentTimeMillis());
        }

        public static class HealthStatus {
            private final boolean healthy;
            private final String message;
            private final long timestamp;

            public HealthStatus(boolean healthy, String message, long timestamp) {
                this.healthy = healthy;
                this.message = message;
                this.timestamp = timestamp;
            }

            public boolean isHealthy() { return healthy; }
            public String getMessage() { return message; }
            public long getTimestamp() { return timestamp; }
        }
    }
}