package com.stockpulse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Stateless API security: validate issuer, signature, timestamps, audience, and endpoint permissions. */
@Configuration
public class SecurityConfig {
    private static final Logger log=LoggerFactory.getLogger(SecurityConfig.class);

    @Bean SecurityFilterChain security(HttpSecurity http,ObjectMapper mapper,JwtAuthenticationConverter jwtConverter) throws Exception {
        return http.csrf(csrf->csrf.disable()).cors(Customizer.withDefaults())
                .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth->auth
                        .requestMatchers("/actuator/health","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/v1/products","/api/v1/products/**","/api/v1/strategy").hasAnyAuthority("SCOPE_stockpulse.read","ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/products").hasAnyAuthority("SCOPE_inventory.write","ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PATCH,"/api/v1/products/*/stock").hasAnyAuthority("SCOPE_inventory.write","ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/products/*/orders").hasAnyAuthority("SCOPE_inventory.write","ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/v1/products/*/suggest-pricing","/api/v1/products/*/suggest-reorder").hasAnyAuthority("SCOPE_suggestions.write","ROLE_MERCHANDISER","ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PATCH,"/api/v1/pricing-suggestions/**","/api/v1/reorder-suggestions/**").hasAnyAuthority("SCOPE_suggestions.review","ROLE_MERCHANDISER","ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT,"/api/v1/strategy").hasAnyAuthority("SCOPE_strategy.admin","ROLE_ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth->oauth
                        .authenticationEntryPoint((request,response,exception)->writeError(mapper,response,401,"INVALID_TOKEN","Your sign-in session is invalid. Sign in again.",request.getRequestURI()))
                        .jwt(jwt->jwt.jwtAuthenticationConverter(jwtConverter)))
                .exceptionHandling(errors->errors
                        .authenticationEntryPoint((request,response,exception)->writeError(mapper,response,401,"AUTHENTICATION_REQUIRED","Sign in to continue.",request.getRequestURI()))
                        .accessDeniedHandler((request,response,exception)->writeError(mapper,response,403,"ACCESS_DENIED","Your account does not have permission to do that.",request.getRequestURI())))
                .build();
    }

    @Bean JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:9000/realms/stockpulse}") String issuer,
            @Value("${stockpulse.security.jwk-set-uri:http://localhost:9000/realms/stockpulse/protocol/openid-connect/certs}") String jwkSetUri,
            @Value("${stockpulse.security.audience:stockpulse-api}") String audience) {
        NimbusJwtDecoder decoder=NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> audienceValidator=jwt->jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Required token audience is missing",""));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),audienceValidator));
        return decoder;
    }

    @Bean JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes=new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter=new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt->{
            Set<GrantedAuthority> authorities=new HashSet<>(Optional.ofNullable(scopes.convert(jwt)).orElse(List.of()));
            addRoles(authorities,jwt.getClaim("roles"));
            Object realmAccess=jwt.getClaim("realm_access");
            if(realmAccess instanceof Map<?,?> realm) addRoles(authorities,realm.get("roles"));
            return authorities;
        });
        return converter;
    }

    private static void addRoles(Set<GrantedAuthority> authorities,Object roles) {
        if(roles instanceof Collection<?> values) for(Object value:values) if(value instanceof String role&&!role.isBlank()) {
            authorities.add(new SimpleGrantedAuthority(role.startsWith("ROLE_")?role:"ROLE_"+role));
        }
    }
    private static void writeError(ObjectMapper mapper,jakarta.servlet.http.HttpServletResponse response,int status,String code,String message,String path) throws java.io.IOException {
        String requestId=UUID.randomUUID().toString(); response.setStatus(status); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if(status==401) response.setHeader("WWW-Authenticate","Bearer");
        response.getWriter().write(mapper.writeValueAsString(Map.of("code",code,"message",message,"requestId",requestId)));
        log.warn("security_error code={} requestId={} status={} path={}",code,requestId,status,path);
    }

    @Bean CorsConfigurationSource corsConfigurationSource(@Value("${stockpulse.cors-origins:http://localhost:5173}") String origins) {
        CorsConfiguration c=new CorsConfiguration(); c.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList());
        c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","OPTIONS")); c.setAllowedHeaders(List.of("Content-Type","Authorization"));
        c.setExposedHeaders(List.of("WWW-Authenticate"));
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",c); return source;
    }
}
