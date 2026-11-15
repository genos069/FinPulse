package in.expensetrackerapp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

@Configuration
@EnableConfigurationProperties(GridFsProperties.class)
public class GridFsConfig {

    /**
     * Defining this bean replaces Spring Boot's auto-configured GridFsTemplate so the
     * bucket name comes from application.gridfs.bucket instead of a Boot property name.
     */
    @Bean
    public GridFsTemplate gridFsTemplate(
            MongoDatabaseFactory databaseFactory,
            MongoConverter mongoConverter,
            GridFsProperties properties) {
        return new GridFsTemplate(databaseFactory, mongoConverter, properties.getBucket());
    }
}
