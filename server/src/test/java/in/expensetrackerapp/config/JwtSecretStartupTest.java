package in.expensetrackerapp.config;

import in.expensetrackerapp.auth.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecretStartupTest {

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfig {
        @Bean
        public JwtService jwtService(JwtProperties jwtProperties) {
            return new JwtService(jwtProperties);
        }
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void testStartupFailsWhenJwtSecretIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class);
            assertThat(context.getStartupFailure().getCause().getCause().getMessage())
                    .contains("JWT_SECRET configuration is missing");
        });
    }

    @Test
    void testStartupFailsWhenJwtSecretIsTooShort() {
        contextRunner
                .withPropertyValues("application.jwt.secret=too-short")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure().getCause().getCause().getMessage())
                            .contains("JWT_SECRET is too short");
                });
    }

    @Test
    void testStartupSucceedsWhenJwtSecretIsValid() {
        contextRunner
                .withPropertyValues("application.jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW11c3QtYmUtYXQtbGVhc3QtMjU2LWJpdHM=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtService.class);
                });
    }
}
