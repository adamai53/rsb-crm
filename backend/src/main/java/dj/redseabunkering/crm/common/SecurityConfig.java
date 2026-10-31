package dj.redseabunkering.crm.common;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import dj.redseabunkering.crm.campaign.PublicFormProperties;

/**
 * API sans état : access token JWT dans l'en-tête Authorization, refresh token dans un cookie
 * HttpOnly SameSite=Strict (d'où l'absence de CSRF). Les droits fins sont vérifiés par @PreAuthorize.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtConverter)
            throws Exception {
        var unauthorized = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized))
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(unauthorized))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout", "/api/auth/verify-email",
                                "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        // Exposé seulement si springdoc est activé (SWAGGER_ENABLED / profil dev)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props, PublicFormProperties form) {
        // Le site vitrine n'atteint que le formulaire public, sans cookie ; à déclarer avant la règle générale
        var publicForm = new CorsConfiguration();
        publicForm.setAllowedOrigins(form.allowedOrigins());
        publicForm.setAllowedMethods(List.of("POST", "OPTIONS"));
        publicForm.setAllowedHeaders(List.of("Content-Type", "X-Api-Key"));
        publicForm.setAllowCredentials(false);

        var config = new CorsConfiguration();
        config.setAllowedOrigins(props.corsAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/public/leads", publicForm);
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
