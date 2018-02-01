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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
class DefaultOperationLogger implements OperationLogger {

    private Logger logger = LoggerFactory.getLogger(DefaultOperationLogger.class);

    private ObjectMapper objectMapper = new ObjectMapper();

    private SimpleDateFormat dateFormat;

    private final Sink sink;

    private final String component;

    private final String operation;

    public DefaultOperationLogger(Sink sink, String component, String operation) {
        this.sink = sink;
        this.component = component;
        this.operation = operation;
        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    }

    @Override
    public void log(Level logLevel, String context, String message, Object... messageArguments) {
        log(logLevel, context, null, message, messageArguments);
    }

    @Override
    public void log(Level logLevel, String context, Date expires, String message, Object... messageArguments) {
        String formattedMessage = String.format(message, messageArguments);

        Map<String, String> messageMap = new HashMap<>();
        messageMap.put("time", dateFormat.format(new Date()));
        messageMap.put("component", component);
        messageMap.put("operation", operation);
        messageMap.put("context", context);
        messageMap.put("log-level", logLevel.name());
        messageMap.put("message", formattedMessage);
        if (expires != null) {
            messageMap.put("expires", dateFormat.format(expires));
        } else {
            messageMap.put("expires", "");
        }

        Consumer<Exception> exceptionHandler = (e) -> {
            //TODO stop the application
            logger.error("Failed to deliver operation log!", e);
        };
        try {
            sink.send(objectMapper.writeValueAsString(messageMap), exceptionHandler);
        } catch (JsonProcessingException e) {
            logger.error("Cannot prepare log message.", e);
        }
    }
}
