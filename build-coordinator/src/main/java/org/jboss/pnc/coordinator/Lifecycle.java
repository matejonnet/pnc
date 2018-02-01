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
package org.jboss.pnc.coordinator;

import org.jboss.pnc.common.json.moduleconfig.SystemConfig;
import org.jboss.pnc.logging.KafkaSink;
import org.jboss.pnc.logging.OperationLoggerFactory;
import org.jboss.pnc.logging.Sink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import java.io.IOException;

/**
 *
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2014-12-16.
 */
@Dependent
public class Lifecycle {

    private static final Logger log = LoggerFactory.getLogger(Lifecycle.class);

    private OperationLoggerConfiguration operationLoggerConfiguration;

    public Lifecycle() {
    }

    @Inject
    public Lifecycle(SystemConfig systemConfig) {
        operationLoggerConfiguration = systemConfig.getOperationLoggerConfiguration();
    }

    public void start() {
        Sink sink = new KafkaSink(operationLoggerConfiguration.get(), "ncl-logs");
        OperationLoggerFactory.configure(sink, "pnc-orch");
        log.info("Core started.");
    }

    public void stop() {
        try {
            OperationLoggerFactory.destroy();
        } catch (IOException e) {
            log.error("Cannot destroy OperationLoggerFactory.", e);
        }
        log.info("Core stopped.");
    }


}
