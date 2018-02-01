/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.logging;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class KafkaSink implements Sink {

    private static Logger logger = LoggerFactory.getLogger(KafkaSink.class);

    private KafkaProducer<String, String> kafkaProducer;

    private String topic;

    KafkaSink(Map<String, Object> properties, String topic) {
        this.topic = topic;
        kafkaProducer = new KafkaProducer<>(properties);
    }

    KafkaSink(Properties properties, String topic) {
        this.topic = topic;
        kafkaProducer = new KafkaProducer<>(properties);
    }

    @Override
    public void send(String message, Consumer<Exception> exceptionHandler) {
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, message);
        if (exceptionHandler == null) {
            kafkaProducer.send(producerRecord);
        } else {
            Callback callback = (metadata, exception) -> {
                if (exception != null) {
                    exceptionHandler.accept(exception);
                } else {
                    logger.trace("Message sent to Kafka. Partition:{}, timestamp {}.", metadata.partition(), metadata.timestamp());
                }
            };
            kafkaProducer.send(producerRecord, callback);
        }
    }

    @Override
    public void send(String message, long timeoutMillis) throws TimeoutException, ExecutionException, InterruptedException {
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, message);
        Future<RecordMetadata> future = kafkaProducer.send(producerRecord);
        future.get(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() throws IOException {
        kafkaProducer.close();
    }
}
