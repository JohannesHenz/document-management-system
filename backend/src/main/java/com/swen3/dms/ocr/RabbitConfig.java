package com.swen3.dms.ocr;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology shared with the OCR worker, which declares the same exchange and queues. Messages that cannot be
 * processed are moved to a dead-letter queue instead of being redelivered forever.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "dms.ocr.enabled", havingValue = "true")
public class RabbitConfig {

    public static final String EXCHANGE = "dms.ocr";
    public static final String JOB_QUEUE = "dms.ocr.jobs";
    public static final String RESULT_QUEUE = "dms.ocr.results";
    public static final String JOB_ROUTING_KEY = "job";
    public static final String RESULT_ROUTING_KEY = "result";

    @Bean
    Declarables ocrTopology() {
        DirectExchange exchange = new DirectExchange(EXCHANGE);
        Queue jobs = withDeadLetterQueue(JOB_QUEUE);
        Queue results = withDeadLetterQueue(RESULT_QUEUE);
        Binding jobBinding = BindingBuilder.bind(jobs).to(exchange).with(JOB_ROUTING_KEY);
        Binding resultBinding = BindingBuilder.bind(results).to(exchange).with(RESULT_ROUTING_KEY);
        return new Declarables(exchange, jobs, results, jobBinding, resultBinding,
                QueueBuilder.durable(JOB_QUEUE + ".dead").build(),
                QueueBuilder.durable(RESULT_QUEUE + ".dead").build());
    }

    private static Queue withDeadLetterQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange("")
                .deadLetterRoutingKey(name + ".dead")
                .build();
    }

    /** JSON messages, converted to the listener's parameter type rather than a class name from the sender. */
    @Bean
    MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(DefaultJackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
