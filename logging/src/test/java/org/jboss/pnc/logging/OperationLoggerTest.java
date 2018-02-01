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

import org.assertj.core.api.Assertions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */

public class OperationLoggerTest {

    private static final String LOG_MESSAGE = "And the papers want to know whose shirt you wear.";

    private static SinkMock sink;

    @BeforeClass
    public static void before() {
        sink = new SinkMock();
        OperationLoggerFactory.configure(sink, "pnc-orch");
    }

    @AfterClass
    public static void after() throws IOException {
        OperationLoggerFactory.destroy();
    }

    @Test
    public void shouldLogMessage() {
        OperationLogger operationLogger = OperationLoggerFactory.getLogger("space-odyssey");
        operationLogger.info(Thread.currentThread().getId() + "", LOG_MESSAGE);

        System.out.println(sink.getMessages());

        Assertions.assertThat(sink.getMessages()).contains("{\"component\":\"pnc-orch\",\"log-level\":\"INFO\",\"context\":\"1\",\"message\":\""+ LOG_MESSAGE+"\",\"operation\":\"space-odyssey\"}");
    }
}
