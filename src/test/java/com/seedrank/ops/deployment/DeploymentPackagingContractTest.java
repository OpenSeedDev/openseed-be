package com.seedrank.ops.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DeploymentPackagingContractTest {

    private final Path repositoryRoot = Path.of("").toAbsolutePath();

    @Test
    void buildsOneNonRootJava21ImageThatReceivesSigterm() throws IOException {
        String dockerfile = read("Dockerfile");

        assertThat(dockerfile)
                .contains("AS builder")
                .contains("FROM eclipse-temurin:21-jre")
                .contains("USER app")
                .contains("STOPSIGNAL SIGTERM")
                .contains("ENTRYPOINT");
    }

    @Test
    void separatesHttpApiAndNonWebWorkerProfiles() throws IOException {
        String apiProfile = read("src/main/resources/application-api.yml");
        String workerProfile = read("src/main/resources/application-worker.yml");

        assertThat(apiProfile)
                .contains("web-application-type: servlet")
                .contains("scheduling-enabled: false");
        assertThat(workerProfile)
                .contains("web-application-type: none")
                .contains("scheduling-enabled: true");
    }

    @Test
    void configuresBoundedGracefulShutdownForRequestsAndSchedulers() throws IOException {
        String application = read("src/main/resources/application.yml");

        assertThat(application)
                .contains("shutdown: graceful")
                .contains("timeout-per-shutdown-phase: 30s")
                .contains("await-termination: true")
                .contains("await-termination-period: 30s");
    }

    @Test
    void productionComposeKeepsDatabaseAndApplicationBehindCaddy() throws IOException {
        String compose = read("compose.production.yml");

        assertThat(compose)
                .contains("postgres:")
                .contains("api:")
                .contains("worker:")
                .contains("caddy:")
                .contains("127.0.0.1:")
                .contains("stop_grace_period: 35s")
                .doesNotContain("\"5432:5432\"")
                .doesNotContain("\"8080:8080\"");
    }

    @Test
    void lightsailDeploymentRequiresExternalSecretsAndChecksReadiness() throws IOException {
        String exampleEnvironment = read(".env.production.example");
        String deployScript = read("deploy/lightsail/deploy.sh");

        assertThat(exampleEnvironment)
                .contains("JWT_SECRET=replace-with-")
                .contains("DB_PASSWORD=replace-with-")
                .contains("SITE_ADDRESS=:80");
        assertThat(deployScript)
                .contains("compose --env-file")
                .contains("/actuator/health/readiness")
                .contains("set -Eeuo pipefail");
    }

    @Test
    void continuousIntegrationBuildsTheProductionImage() throws IOException {
        String workflow = read(".github/workflows/backend-ci.yml");

        assertThat(workflow)
                .contains("Build production Docker image")
                .contains("docker build --tag seedrank-backend:ci");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(repositoryRoot.resolve(relativePath));
    }
}
