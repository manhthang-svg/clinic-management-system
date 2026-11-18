package clinic.backend.config;

import jakarta.servlet.DispatcherType;
import clinic.backend.security.RestAccessDeniedHandler;
import clinic.backend.security.RestAuthenticationEntryPoint;
import clinic.backend.security.jwt.RoleAuthoritiesConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/** HTTP security; JWT signing and verification remain in {@link JwtConfig}. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        // Simulated CA app (UC-90): an outside system with its own PIN sign-in; its controller
                        // exists only when clinic.signing-simulator.console-enabled is true (dev profile).
                        .requestMatchers("/dev/ca-sim", "/dev/ca-sim/**").permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/payments/payos-webhook",
                                "/api/v1/payments/confirm-payos-result")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/payments/by-order-code/**")
                        .permitAll()
                        .requestMatchers("/api/v1/me", "/api/v1/me/**").authenticated()
                        .requestMatchers("/api/v1/accounts", "/api/v1/accounts/**").authenticated()
                        .requestMatchers("/api/v1/rooms", "/api/v1/rooms/**").authenticated()
                        .requestMatchers("/api/v1/services", "/api/v1/services/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/lab-parameters", "/api/v1/lab-parameters/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/departments", "/api/v1/departments/**").authenticated()
                        .requestMatchers("/api/v1/departments", "/api/v1/departments/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/clinic-facility").hasRole("ADMIN")
                        .requestMatchers("/api/v1/medicines", "/api/v1/medicines/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/statistics", "/api/v1/statistics/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/record-archives", "/api/v1/record-archives/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/patients", "/api/v1/patients/**").authenticated()
                        .requestMatchers("/api/v1/visits", "/api/v1/visits/**").authenticated()
                        .requestMatchers("/api/v1/clinical-examinations", "/api/v1/clinical-examinations/**").authenticated()
                        .requestMatchers("/api/v1/conclusions", "/api/v1/conclusions/**").authenticated()
                        .requestMatchers("/api/v1/examined-patients").authenticated()
                        .requestMatchers("/api/v1/medical-records", "/api/v1/medical-records/**").authenticated()
                        .requestMatchers("/api/v1/prescriptions", "/api/v1/prescriptions/**").authenticated()
                        .requestMatchers("/api/v1/follow-up-appointments", "/api/v1/follow-up-appointments/**").authenticated()
                        .requestMatchers(
                                "/api/v1/insurance/verifications",
                                "/api/v1/insurance/verifications/**")
                        .authenticated()
                        .requestMatchers(
                                "/api/v1/payments",
                                "/api/v1/payments/**"
                        ).authenticated()
                        .requestMatchers("/api/v1/paraclinical-orders", "/api/v1/paraclinical-orders/**").authenticated()
                        .requestMatchers("/api/v1/technician", "/api/v1/technician/**").authenticated()
                        .requestMatchers("/api/v1/paraclinical-results", "/api/v1/paraclinical-results/**").authenticated()
                        .requestMatchers("/api/v1/duty-schedules", "/api/v1/duty-schedules/**").authenticated()
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new RoleAuthoritiesConverter());
        return converter;
    }
}
