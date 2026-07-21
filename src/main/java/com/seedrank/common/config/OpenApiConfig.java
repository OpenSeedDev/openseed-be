package com.seedrank.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI seedRankOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .info(new Info()
                        .title("SeedRank API")
                        .description("SeedRank 백엔드 API 문서")
                        .version("v1"));
    }

    @Bean
    public OperationCustomizer requestIdHeaderCustomizer() {
        return (operation, handlerMethod) -> operation.addParametersItem(new Parameter()
                .in("header")
                .name("X-Request-Id")
                .required(false)
                .description("선택적 요청 추적 ID입니다. 영문·숫자와 . _ : - 문자로 구성된 1~64자 값만 전파됩니다."));
    }
}
