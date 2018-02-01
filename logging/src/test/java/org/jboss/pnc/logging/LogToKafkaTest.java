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

import org.junit.Ignore;
import org.junit.Test;

import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Properties;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class LogToKafkaTest {

    private static final String LOG_MESSAGE = "Take your protein pills and put your helmet on.";

    private static KafkaSink sink;

    @Ignore
    @Test
    public void shouldLogMessage() throws IOException {
        Properties properties = new Properties();
        properties.load(new FileReader("kafka.properties"));
        sink = new KafkaSink(properties, "ncl-logs");
        OperationLoggerFactory.configure(sink, "pnc-orch");

        OperationLogger operationLogger = OperationLoggerFactory.getLogger("space-odyssey");
        operationLogger.info(Thread.currentThread().getId() + "", Date.from(Instant.now().plus(14, ChronoUnit.DAYS)), LOG_MESSAGE);

        OperationLoggerFactory.destroy();
    }
}
