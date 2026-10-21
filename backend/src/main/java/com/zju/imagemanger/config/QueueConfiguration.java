package com.zju.imagemanger.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

@Configuration
public class QueueConfiguration {
    public static final String PROCESSING_QUEUE = "image.processing.v2";
    public static final String PROCESSING_EXCHANGE = "image.events";
    public static final String ROUTING_KEY = "image.created.v2";
    public static final String DEAD_QUEUE = "image.processing.dlq.v2";

    @Bean MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter("com.zju.imagemanger.service");
    }

    @Bean DirectExchange imageExchange() { return new DirectExchange(PROCESSING_EXCHANGE, true, false); }
    @Bean Queue processingQueue(@Value("${app.queue.max-length:10000}") long maxLength) {
        return QueueBuilder.durable(PROCESSING_QUEUE).deadLetterExchange(PROCESSING_EXCHANGE)
                .deadLetterRoutingKey("image.failed.v2")
                .withArgument("x-max-length", maxLength)
                .withArgument("x-overflow", "reject-publish")
                .build();
    }
    @Bean Queue deadLetterQueue() { return QueueBuilder.durable(DEAD_QUEUE).build(); }
    @Bean Binding processingBinding(@Qualifier("processingQueue") Queue queue, DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }
    @Bean Binding deadLetterBinding(@Qualifier("deadLetterQueue") Queue queue, DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("image.failed.v2");
    }
}
