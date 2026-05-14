package com.h3.h3_java;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class H3JavaApplication {

	public static void main(String[] args) {
		SpringApplication.run(H3JavaApplication.class, args);
	}

}
