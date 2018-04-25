package com.mvcframework.common;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mvcframework.servlet.GPDispatcherServlet;

@Configuration
public class ServletConfig {

	@Bean
	public ServletRegistrationBean gpServletRegistrationBean() {
		ServletRegistrationBean registration = new ServletRegistrationBean(new GPDispatcherServlet());
		registration.setEnabled(true);
		registration.addUrlMappings("*.json");
		return registration;
	}
}
