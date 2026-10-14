package clinic.backend.shared.configuration;

import java.security.SecureRandom;
import java.time.Clock;

import clinic.backend.shared.id.UuidV7Generator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class TimeAndIdConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    UuidV7Generator uuidV7Generator(Clock clock, SecureRandom secureRandom) {
        return new UuidV7Generator(clock, secureRandom);
    }
}
