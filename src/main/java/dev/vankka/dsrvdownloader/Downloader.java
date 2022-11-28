package dev.vankka.dsrvdownloader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Collections;

@SpringBootApplication
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

}
