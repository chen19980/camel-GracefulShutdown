package com.booking.bookingservice.config;

import javax.annotation.PostConstruct;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultResourceResolvers;
import org.apache.camel.saga.InMemorySagaService;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.spi.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@Slf4j
public class CamelConfig extends RouteBuilder {

	private static final int SAGA_RETRY_ATTEMPTS = 1;

	@Autowired
	CamelContext camelContext;

	@Override
	public void configure() throws Exception {

		InMemorySagaService inMemorySagaService = new InMemorySagaService();
		inMemorySagaService.setMaxRetryAttempts(SAGA_RETRY_ATTEMPTS);
		camelContext.addService(inMemorySagaService);

		camelContext.setUseMDCLogging(true);
		camelContext.setLogMask(true);

		//trace
		camelContext.setTracingStandby(true);
		Tracer tracer = camelContext.getTracer();
		tracer.setEnabled(true);

		//log
		camelContext.getGlobalOptions().put(Exchange.LOG_EIP_NAME, "tw.com.firstbank");

		//default 45 seconds
		camelContext.getShutdownStrategy().setTimeout(10);

		//--
		log.debug(">>>> camel context = {}", camelContext.getName());

		//TODO: be sure
		//camelContext.start();
		camelContext.getRouteController().startAllRoutes();
		log.debug(">>>> camel endpoints = {}", camelContext.getEndpoints().stream().map(p -> p.toString()).collect(java.util.stream.Collectors.joining(", ")));
	}

	@Bean
	public ProducerTemplate createCamelProducerTemplate() {
		return camelContext.createProducerTemplate();
	}

	@PostConstruct
	void loadRoutes() {
		//loadYamlRoute("saga.yaml");
		//loadYamlRoute("saga.xml");
	}

	private void loadYamlRoute(String name) {
		ExtendedCamelContext extendedCamelContext = camelContext.adapt(ExtendedCamelContext.class);
		RoutesLoader loader = extendedCamelContext.getRoutesLoader();
		try (DefaultResourceResolvers.ClasspathResolver resolver = new DefaultResourceResolvers.ClasspathResolver()) {
			resolver.setCamelContext(camelContext);

			Resource resource = resolver.resolve("classpath:camel/" + name);
			loader.loadRoutes(resource);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
