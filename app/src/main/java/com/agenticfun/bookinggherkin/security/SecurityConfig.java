package com.agenticfun.bookinggherkin.security;

import com.agenticfun.bookinggherkin.web.ApiErrorFactory;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(BookingSecurityProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BookingSecurityProperties properties,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ApiErrorFactory apiErrorFactory) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            apiErrorFactory.write(response, HttpStatus.UNAUTHORIZED,
                                    "Authentication is required to access this resource",
                                    request);
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            apiErrorFactory.write(response, HttpStatus.FORBIDDEN,
                                    "Access is denied",
                                    request);
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        if (properties.isEnabled()) {
            http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/actuator/health", "/api-docs/openapi.json", "/swagger-ui/**").permitAll()
                    .requestMatchers("/actuator/metrics", "/actuator/metrics/**").hasRole("ADMIN")
                    .requestMatchers("/bookings", "/bookings/**").authenticated()
                    .requestMatchers("/api/v1/bookings", "/api/v1/bookings/**").authenticated()
                    .anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        }

        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }
}
