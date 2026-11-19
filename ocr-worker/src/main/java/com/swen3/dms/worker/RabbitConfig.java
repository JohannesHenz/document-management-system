package com.swen3.dms.worker;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Must declare exactly the same topology as the backend's RabbitConfig, or RabbitMQ rejects the declaration. */
@Configuration(proxyBeanMethods = false)
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
        return new Declarables(exchange, jobs, results,
                BindingBuilder.bind(jobs).to(exchange).with(JOB_ROUTING_KEY),
                BindingBuilder.bind(results).to(exchange).with(RESULT_ROUTING_KEY),
                QueueBuilder.durable(JOB_QUEUE + ".dead").build(),
                QueueBuilder.durable(RESULT_QUEUE + ".dead").build());
    }

    private static Queue withDeadLetterQueue(String name) {
        return QueueBuilder.durable(name).deadLetterExchange("").deadLetterRoutingKey(name + ".dead").build();
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(DefaultJackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
