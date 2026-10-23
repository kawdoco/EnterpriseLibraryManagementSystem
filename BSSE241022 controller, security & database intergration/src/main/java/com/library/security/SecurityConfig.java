package com.library.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every page requires an authenticated ADMIN or STAFF user, except the login page and static assets.
 * CSRF protection stays ENABLED (Spring default): all POST forms carry a token via th:action,
 * and JavaScript calls read it from the <meta name="_csrf"> tags in the layout.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/vendor/**", "/images/**", "/favicon.ico").permitAll()
                        // Admin only: money, reports/audit, blacklist management, deactivating records
                        .requestMatchers("/fines/**", "/reports/**", "/members/blacklisted",
                                "/members/*/blacklist", "/members/*/clear-blacklist",
                                "/members/*/deactivate", "/members/*/activate",
                                "/books/*/deactivate", "/books/*/activate").hasRole("ADMIN")
                        // Everyday circulation work: counter, members, books, transactions
                        .anyRequest().hasAnyRole("ADMIN", "STAFF"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")                 // POST only (CSRF-protected)
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .sessionManagement(sm -> sm.sessionFixation(fix -> fix.migrateSession()));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
