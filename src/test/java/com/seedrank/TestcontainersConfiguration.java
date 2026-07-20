package com.seedrank;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // postgres:17-alpine 설정으로 테스트와 로컬 개발에서 PostgreSQL 주 버전이 달라지는 것을 방지
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

}
