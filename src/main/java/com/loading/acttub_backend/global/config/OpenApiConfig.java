package com.loading.acttub_backend.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
		info = @Info(
				title = "Acttub Backend API",
				version = "v1",
				description = "Acttub 코칭 생성 및 평가 API"
		)
)
public class OpenApiConfig {
}
