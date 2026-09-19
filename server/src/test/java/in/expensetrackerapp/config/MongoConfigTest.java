package in.expensetrackerapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class MongoConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MongoAutoConfiguration.class,
                    DataMongoAutoConfiguration.class
            ));

    @Test
    void applicationPropertiesShouldConfigureSpringMongoUriWithFallback() throws Exception {
        Path mainPropertiesPath = Paths.get("src/main/resources/application.properties");
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(mainPropertiesPath)) {
            properties.load(reader);
        }

        assertThat(properties.getProperty("spring.mongodb.uri"))
                .isEqualTo("${MONGODB_URI:mongodb://localhost:27017/expensetrackerdb}");
        assertThat(properties.getProperty("spring.data.mongodb.uri"))
                .as("Deprecated spring.data.mongodb.uri must not be used in Spring Boot 4.x")
                .isNull();
    }

    @Test
    void shouldBindSpringMongoUriPropertyAndConfigureDatabase() {
        contextRunner
                .withPropertyValues("spring.mongodb.uri=mongodb://localhost:27017/expensetrackerdb")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MongoProperties.class);

                    MongoProperties properties = context.getBean(MongoProperties.class);
                    assertThat(properties.getUri()).isEqualTo("mongodb://localhost:27017/expensetrackerdb");
                    assertThat(properties.getMongoClientDatabase()).isEqualTo("expensetrackerdb");

                    MongoDatabaseFactory databaseFactory = context.getBean(MongoDatabaseFactory.class);
                    assertThat(databaseFactory.getMongoDatabase().getName()).isEqualTo("expensetrackerdb");

                    MongoTemplate mongoTemplate = context.getBean(MongoTemplate.class);
                    assertThat(mongoTemplate.getDb().getName()).isEqualTo("expensetrackerdb");
                });
    }

    @Test
    void shouldBindCustomMongoUriWhenProvided() {
        contextRunner
                .withPropertyValues("spring.mongodb.uri=mongodb://localhost:27017/custom_expense_db")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MongoProperties properties = context.getBean(MongoProperties.class);
                    assertThat(properties.getMongoClientDatabase()).isEqualTo("custom_expense_db");

                    MongoDatabaseFactory databaseFactory = context.getBean(MongoDatabaseFactory.class);
                    assertThat(databaseFactory.getMongoDatabase().getName()).isEqualTo("custom_expense_db");
                });
    }

    @Test
    void shouldDemonstrateLegacyPropertyDoesNotBindInSpringBoot4() {
        // Regression test: spring.data.mongodb.uri is ignored by Spring Boot 4's MongoProperties,
        // causing determineUri() to fall back to mongodb://localhost/test
        contextRunner
                .withPropertyValues("spring.data.mongodb.uri=mongodb://localhost:27017/expensetrackerdb")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MongoProperties properties = context.getBean(MongoProperties.class);
                    assertThat(properties.getUri()).isNull();
                    assertThat(properties.getMongoClientDatabase()).isEqualTo("test");
                });
    }
}
