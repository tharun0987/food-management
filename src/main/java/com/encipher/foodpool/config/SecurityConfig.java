package com.encipher.foodpool.config;

import com.encipher.foodpool.model.Employee;
import com.encipher.foodpool.service.EmployeeService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;

import java.util.*;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {
    
    private final EmployeeService employeeService;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/error", "/static/**", "/css/**", "/js/**", "/test/**").permitAll()
                // Admin pages - require ADMIN or CONTRIBUTOR role
                .requestMatchers("/admin", "/admin/", "/admin/menu", "/admin/menu/**", "/admin/pool/**").hasAnyRole("ADMIN", "CONTRIBUTOR")
                // Employee management - only ADMIN
                .requestMatchers("/admin/employees", "/admin/employees/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth -> oauth
                .loginPage("/")
                .defaultSuccessUrl("/home", true)
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(oauth2UserService())
                )
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
            );
        
        return http.build();
    }
    
    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService() {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        
        return request -> {
            OAuth2User oauth2User = delegate.loadUser(request);
            
            String email = oauth2User.getAttribute("Email");
            String displayName = oauth2User.getAttribute("Display_Name");
            
            if (displayName == null) {
                displayName = oauth2User.getAttribute("First_Name");
            }
            
            log.info("OAuth2 Login: {} - {}", displayName, email);
            
            // Find employee
            Employee employee = employeeService.findOrMatch(displayName, email);
            
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            
            Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());
            
            if (employee != null) {
                attributes.put("employeeId", employee.getEmployeeId());
                attributes.put("employeeName", employee.getName());
                attributes.put("isRegistered", true);
                attributes.put("role", employee.getRole() != null ? employee.getRole() : "USER");
                
                // Grant roles based on employee role
                if (employee.isAdministrator()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    attributes.put("isAdmin", true);
                    attributes.put("isAdministrator", true);
                    log.info("User {} granted ADMINISTRATOR role", employee.getName());
                } else if (employee.isContributor()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_CONTRIBUTOR"));
                    attributes.put("isContributor", true);
                    attributes.put("isAdmin", true);  // For UI showing admin link
                    log.info("User {} granted CONTRIBUTOR role", employee.getName());
                }
                
                log.info("Employee found: {} - {} (Role: {})", employee.getEmployeeId(), employee.getName(), employee.getRole());
            } else {
                attributes.put("isRegistered", false);
                log.warn("Employee not found for: {} - {}", displayName, email);
            }
            
            return new DefaultOAuth2User(authorities, attributes, "Email");
        };
    }
}
