package com.ecommerce.order.mgmt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrderManagementApplication {

	public static void main(String[] args) {
		// Enable full async logging via LMAX Disruptor — must be set before any logger is created
		System.setProperty("log4j2.contextSelector",
				"org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
		SpringApplication.run(OrderManagementApplication.class, args);
	}

}
