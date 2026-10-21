package com.zju.imagemanger.service;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.*;

class RabbitJsonMessageConverterTest {
    @Test
    void convertsProcessingEventAsJson() {
        MessageConverter converter = new org.springframework.amqp.support.converter.JacksonJsonMessageConverter(
                "com.zju.imagemanger.service");
        var event = new OutboxPublisher.ImageCreatedMessage(8L, 5L, 3L);

        Message encoded = converter.toMessage(event, new MessageProperties());

        assertTrue(new String(encoded.getBody(), java.nio.charset.StandardCharsets.UTF_8).contains("\"imageId\":5"));
        assertEquals("application/json", encoded.getMessageProperties().getContentType());
        assertEquals(event, converter.fromMessage(encoded));
    }
}
