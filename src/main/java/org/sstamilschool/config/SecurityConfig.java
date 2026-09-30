package org.sstamilschool.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import org.sstamilschool.service.SstsUserDetailsService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private SstsUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/", "/login", "/register", "/forgot-password", "/reset-password", "/css/**", "/js/**", "/images/**", "/error").permitAll()
                .requestMatchers("/about", "/team", "/calendar", "/contact", "/gallery").permitAll()
                .requestMatchers("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/team/**", "/api/calendar/**", "/api/gallery/**")
                    .hasAnyRole("ADMIN", "SUPER_ADMIN")
                // Super-admin module space: unauthenticated/expired users are sent
                // to /login with a saved request (continue after login); authenticated
                // non-super-admins get 403. See AGENTS.md "Roles & auth".
                .requestMatchers("/superadmin/**").hasRole("SUPER_ADMIN")
                .anyRequest().authenticated()
            )
            .userDetailsService(userDetailsService)
            .formLogin(login -> login
                .loginPage("/login")
                .loginProcessingUrl("/login")
                // RoleAwareAuthenticationSuccessHandler is the single source of truth for
                // the post-login target. It extends SavedRequestAwareAuthenticationSuccessHandler,
                // so a saved request (e.g. /superadmin/** hit while logged out) takes
                // precedence; only when no saved request exists does the handler's own
                // determineTargetUrl(...) choose /superadmin for SUPER_ADMIN or /dashboard
                // otherwise. (It does not use a TargetUrlDeterminer -- it overrides the
                // determineTargetUrl(...) method directly.)
                .successHandler(new RoleAwareAuthenticationSuccessHandler())
                .failureUrl("/login?error=true")
                .usernameParameter("username")
                .passwordParameter("password")
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );
        return http.build();
    }
}
