package com.scucourse.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("login").setViewName("login");
        registry.addViewController("login.html").setViewName("login");
        registry.addViewController("register").setViewName("register");
        registry.addViewController("register.html").setViewName("register");
        registry.addViewController("forgot-password").setViewName("forgot-password");
        registry.addViewController("forgot-password.html").setViewName("forgot-password");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginHandlerInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/css/*","/js/*","/images/*","/scss/*","/vendor/*","/login","/login.html","/userLogin","/register", "/register.html","/forgot-password", "/forgot-password.html");
    }
}
