package com.fencgut961.aad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AadApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(AadApplication.class);
		app.setWebApplicationType(WebApplicationType.NONE);
		app.run(args);
	}

}