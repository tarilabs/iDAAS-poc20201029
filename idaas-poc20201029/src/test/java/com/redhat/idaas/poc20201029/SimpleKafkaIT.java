package com.redhat.idaas.poc20201029;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public class SimpleKafkaIT extends CamelTestSupport {

    @ClassRule
    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"));

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").to("kafka:ktopic?brokers=" + kafka.getBootstrapServers());
            }
        };
    }

    @Test
    public void test() throws Exception {
        template.requestBody("direct:start", "test");
        template.requestBody("direct:start", "test2");

        Map<String, Object> consumerConfig = new HashMap<>();
        consumerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "tc-" + UUID.randomUUID());
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (final KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerConfig, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(Collections.singletonList("ktopic"));

            final ArrayList<String> events = new ArrayList<>();

            Awaitility.await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                final ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (final ConsumerRecord<String, String> record : records) {
                    events.add(record.value());
                    System.out.println(record);
                }
                assertEquals(2, events.size());
            });
        }
    }
}