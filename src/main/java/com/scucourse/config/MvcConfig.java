package com.scucourse.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
//        registry.addViewController("index").setViewName("index");
//        registry.addViewController("index.html").setViewName("index");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginHandlerInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/css/*","/js/*","/images/*","/scss/*","/vendor/*",
                        "/userLogin","/userRegister","/login","login.html","/register", "/register.html","/forgot-password", "/forgot-password.html");
    }
}
