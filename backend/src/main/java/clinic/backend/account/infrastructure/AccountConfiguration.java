package clinic.backend.account.infrastructure;

import clinic.backend.account.application.SessionPolicy;
import clinic.backend.shared.configuration.ClinicSecurityProperties;
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
