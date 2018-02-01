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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Scanner;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public interface OperationLogger {

    void log(Level logLevel, String context, String message, Object... messageArguments);

    void log(Level logLevel, String context, Date expires, String message, Object... messageArguments);

    default void info(String context, String message, Object... messageArguments) {
        log(Level.INFO, context, message, messageArguments);
    }

    default void info(String context, Date expires, String message, Object... messageArguments) {
        log(Level.INFO, context, expires, message, messageArguments);
    }

    default void warn(String context, String message, Object... messageArguments) {
        log(Level.WARN, context, message, messageArguments);
    }

    default void warn(String context, Date expires, String message, Object... messageArguments) {
        log(Level.WARN, context, expires, message, messageArguments);
    }

    default void error(String context, String message, Object... messageArguments) {
        log(Level.ERROR, context, message, messageArguments);
    }

    default void error(String context, Date expires, String message, Object... messageArguments) {
        log(Level.ERROR, context, expires, message, messageArguments);
    }

    default void error(String context, String message, Throwable e) {
        error(context, null, message, e);
    }

    default void error(String context, Date expires, String message, Throwable e) {
        log(Level.ERROR, context, expires, message);
        StringWriter stackTraceWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stackTraceWriter));
        StringBuffer buffer = stackTraceWriter.getBuffer();

        Scanner scanner = new Scanner(buffer.toString());
        while (scanner.hasNextLine()) {
            log(Level.ERROR, context, expires, scanner.nextLine());
        }
    }

    enum Level {
        INFO, DEBUG, WARN, ERROR;
    }

}
