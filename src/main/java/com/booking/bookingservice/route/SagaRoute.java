package com.booking.bookingservice.route;

import com.booking.bookingservice.model.OrderDto;
import com.booking.bookingservice.service.CreditService;
import com.booking.bookingservice.service.OrderManagerService;
import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.saga.InMemorySagaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class SagaRoute extends RouteBuilder {

    private final OrderManagerService orderManagerService;
    private final CreditService creditService;

    public SagaRoute(OrderManagerService orderManagerService, CreditService creditService) {
        this.orderManagerService = orderManagerService;
        this.creditService = creditService;
    }

    @Autowired
    CamelContext camelContext;


    @Override
    public void configure() throws Exception {
    	
    	camelContext.getShutdownStrategy().setTimeout(20);
    	camelContext.setShutdownRunningTask(ShutdownRunningTask.CompleteCurrentTaskOnly);
    	
    	
        //Step 1
        getContext().addService(new InMemorySagaService());

        from("direct:order")
                .process(exchange ->
                        {
                            exchange.getMessage().setHeader("id", UUID.randomUUID().toString());
                            OrderDto order = exchange.getMessage().getBody(OrderDto.class);
                            order.setOrderId(exchange.getMessage().getHeader("id", String.class));
                            exchange.getMessage().setBody(order);
                        }
                )
                .log(LoggingLevel.INFO, "Id: ${header.id}, Order Received: ${body}")
                .saga()

                .timeout(60, TimeUnit.SECONDS)
                .propagation(SagaPropagation.REQUIRES_NEW)
                .compensation("direct:wholecancel")

                .log("@@@ time to newOrder " + Instant.now().toString())
                .to("direct:newOrder")

                .log("@@@ time to makePayment " + Instant.now().toString())
                .to("direct:makePayment")

                .log("@@@ time to shipOrder " + Instant.now().toString())
                .to("direct:shipOrder")

                .log("@@@ time end " + Instant.now().toString())
                .log("saga Done");

        from("direct:newOrder")
                .saga()
                .propagation(SagaPropagation.MANDATORY)
                .option("id", header("id"))
                .setBody(body())
                .compensation("direct:cancelOrder")
                .bean(orderManagerService, "newOrder")
                .log("ID: ${header.id}, Order ${body} created");

        from("direct:makePayment")
                .saga()
                .propagation(SagaPropagation.MANDATORY)
                .option("id", header("id"))
                .option("body", body())
                .option("customerId", simple("${body.customerId}"))
                .compensation("direct:refundPayment")
                .bean(creditService, "makePayment");

        from("direct:shipOrder")
                .saga()
                .propagation(SagaPropagation.MANDATORY)
                .option("id", header("id"))
                .option("body", body())
                .option("customerId", simple("${body.customerId}"))
                .compensation("direct:cancelShipping")
                .completion("direct:completeShipping")
                .bean(orderManagerService, "shipOrder");




        // compensation
        from("direct:cancelOrder")
                .log("ID: ${header.id}, Order ${body} cancelling")
                .bean(orderManagerService, "cancelOrder")
                .log("ID: ${header.id}, Order ${body} Cancelled");

        // compensation
        from("direct:refundPayment")
                .bean(creditService, "refundPayment");

        // compensation
        from("direct:cancelShipping")
                .bean(orderManagerService, "cancelShipping");

        // compensation
        from("direct:wholecancel")
                .log("@@@ saga route timeout");


        from("direct:completeShipping")
                .bean(orderManagerService, "completeShipping");

    }
}
