package in.expensetrackerapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class GridFsConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MongoAutoConfiguration.class,
                    DataMongoAutoConfiguration.class))
            .withUserConfiguration(GridFsConfig.class)
            .withPropertyValues("spring.mongodb.uri=mongodb://localhost:27017/finpulse");

    @Test
    void shouldDefaultToStandardFsBucket() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(GridFsProperties.class).getBucket()).isEqualTo("fs");
            assertThat(context).hasSingleBean(GridFsTemplate.class);
        });
    }

    @Test
    void shouldBindConfiguredBucketName() {
        contextRunner
                .withPropertyValues("application.gridfs.bucket=statements")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GridFsProperties.class).getBucket()).isEqualTo("statements");
                });
    }

    @Test
    void shouldFailStartupWhenBucketNameIsBlank() {
        contextRunner
                .withPropertyValues("application.gridfs.bucket= ")
                .run(context -> assertThat(context).hasFailed());
    }
}
