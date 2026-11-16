package com.microservice.identity;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Rende operativa la libreria nei servizi servlet con una sola dipendenza nel pom.
 *
 * <p>Il filtro e' registrato come bean ma non nel container servlet: ogni servizio lo
 * aggancia alla propria SecurityFilterChain, dove puo' precedere l'autorizzazione.</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(JwtIdentityProperties.class)
public class IdentityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtIdentityParser jwtIdentityParser(JwtIdentityProperties properties) {
        return new JwtIdentityParser(properties.secret(), properties.issuer());
    }

    @Bean
    @ConditionalOnMissingBean
    public IdentityResolver identityResolver() {
        return new SecurityContextIdentityResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdentityFilter identityFilter(JwtIdentityParser parser) {
        return new IdentityFilter(parser);
    }

    @Bean
    public FilterRegistrationBean<IdentityFilter> identityFilterRegistration(IdentityFilter identityFilter) {
        FilterRegistrationBean<IdentityFilter> registration = new FilterRegistrationBean<>(identityFilter);
        registration.setEnabled(false);
        return registration;
    }
}