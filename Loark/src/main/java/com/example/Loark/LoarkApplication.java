package com.example.Loark;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LoarkApplication {

	public static void main(String[] args) {
		SpringApplication.run(LoarkApplication.class, args);
	}

}
