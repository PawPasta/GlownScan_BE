package com.pawpasta.glowscan_be.gemini.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiConfiguration {

    @Value("${app.gemini.baseURL}")
    private static String GEMINI_STRING_FINAL_LINK;

    @Bean
    RestClient geminiRestClient(GeminiProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(properties.timeout());

        return RestClient.builder()
                .baseUrl(GEMINI_STRING_FINAL_LINK)
                .defaultHeader("x-goog-api-key", properties.apiKey())
                .requestFactory(requestFactory)
                .build();
    }
}
