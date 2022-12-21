package dev.vankka.dsrvdownloader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.Api;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.oas.annotations.EnableOpenApi;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.Collections;

@SpringBootApplication
@EnableSwagger2
@EnableOpenApi
public class Downloader {

    public static Logger LOGGER = LoggerFactory.getLogger("Downloader");
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    public static String GITHUB_URL = "https://api.github.com";

    public static void main(String[] args) {
        int portInt = 8080;
        String port = System.getenv("DOWNLOADER_PORT");
        if (StringUtils.isNotEmpty(port)) {
            portInt = Integer.parseInt(port);
        }

        SpringApplication application = new SpringApplication(Downloader.class);
        application.setDefaultProperties(Collections.singletonMap("server.port", portInt));
        application.run(args);
    }

    @Bean
    public Docket v2swagger() {
        return new Docket(DocumentationType.SWAGGER_2)
                .useDefaultResponseMessages(false)
                .groupName("v2")
                .apiInfo(
                        new ApiInfoBuilder()
                                .title("DiscordSRV Download")
                                .version("v2")
                                .build()
                )
                .select()
                .apis(RequestHandlerSelectors.withClassAnnotation(Api.class))
                .paths(PathSelectors.any())
                .build();
    }
}
