package com.discord.stonks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
		"com.discord.stonks",
		"commands",
		"events",
		"services"
})
@EnableScheduling
public class StonksApplication {

	public static void main(String[] args) {
		SpringApplication.run(StonksApplication.class, args);
	}
}
