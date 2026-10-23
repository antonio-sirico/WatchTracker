package it.watchtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WatchTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WatchTrackerApplication.class, args);
    }
}
