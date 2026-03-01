package com.multibank.aggregationservice;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AggregationserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AggregationserviceApplication.class, args);
	}

	@Bean
	public MeterRegistry meterRegistry() {
		return new SimpleMeterRegistry();
	}

}
