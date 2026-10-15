package clinic.backend.config;

import clinic.backend.config.ClinicSecurityProperties;
import clinic.backend.service.SessionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AccountConfiguration {

    @Bean
    SessionPolicy sessionPolicy(ClinicSecurityProperties properties) {
        ClinicSecurityProperties.Session session = properties.session();
        return new SessionPolicy(session.idleTimeout(), session.absoluteLifetime(), session.retentionAfterEnd());
    }
}
