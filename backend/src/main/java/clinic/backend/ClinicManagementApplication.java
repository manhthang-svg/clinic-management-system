package clinic.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ClinicManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicManagementApplication.class, args);
    }
}
