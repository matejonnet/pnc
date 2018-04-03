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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class OperationLoggerFactory {

    private static Logger logger = LoggerFactory.getLogger(OperationLoggerFactory.class);

    private static Sink sink;

    private static String component;

    private static Consumer<Long> successHandler;

    private static Consumer<Exception> exceptionHandler;

    public static void configure(Sink sink, String component) {
        OperationLoggerFactory.sink = sink;
        OperationLoggerFactory.component = component;
        OperationLoggerFactory.successHandler = (timestamp) -> {};
        OperationLoggerFactory.exceptionHandler = (e) -> {
            logger.error("Cannot write log.", e);
        };
    }

    public static void configure(Sink sink, String component, Consumer<Long> successHandler, Consumer<Exception> exceptionHandler) {
        OperationLoggerFactory.sink = sink;
        OperationLoggerFactory.component = component;
        OperationLoggerFactory.successHandler = successHandler;
        OperationLoggerFactory.exceptionHandler = exceptionHandler;
    }

    public static OperationLogger getLogger() {
        return getLogger("");
    }

    public static OperationLogger getLogger(String operation) {
        return new DefaultOperationLogger(sink, component, operation, successHandler, exceptionHandler);
    }

    public static void destroy(long timeout, TimeUnit timeUnit) throws IOException {
        if (sink != null) {
            sink.close(timeout, timeUnit);
        }
    }

    public static void destroy() throws IOException {
        if (sink != null) {
            sink.close();
        }
    }
}
