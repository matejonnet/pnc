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
package org.jboss.pnc.bpm;

import org.jboss.pnc.bpm.task.BpmBuildTask;
import org.jboss.pnc.common.Configuration;
import org.jboss.pnc.common.json.ConfigurationParseException;
import org.jboss.pnc.common.json.moduleconfig.BpmModuleConfig;
import org.jboss.pnc.common.json.moduleprovider.PncConfigProvider;
import org.jboss.pnc.mock.model.MockUser;
import org.jboss.pnc.model.BuildConfiguration;
import org.jboss.pnc.model.BuildConfigurationAudited;
import org.jboss.pnc.spi.coordinator.BuildTask;
import org.jboss.pnc.spi.exception.CoreException;
import org.jboss.pnc.test.category.DebugTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.kie.api.event.process.DefaultProcessEventListener;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.services.client.api.RemoteRuntimeEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Date;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Category({ DebugTest.class })
public class ProcessEventListenerRemoteTest {

    private static final Logger log = LoggerFactory.getLogger(ProcessEventListenerRemoteTest.class);

    @Test
    public void shouldStartTheProcessAndGetEventUpdates() throws CoreException, ConfigurationParseException {
        Configuration configuration = new Configuration();
        BpmModuleConfig bpmConfig = configuration.getModuleConfig(new PncConfigProvider<>(BpmModuleConfig.class));

        KieSession kieSession = initKieSession(bpmConfig);
        addListener(kieSession);

        BuildTask buildTask = createBuildTask();
        BpmBuildTask bpmBuildTask = new BpmBuildTask(buildTask);

        kieSession.startProcess(bpmConfig.getComponentBuildProcessId(), bpmBuildTask.getExtendedProcessParameters());

    }

    private BuildTask createBuildTask() {
        BuildConfiguration buildConfiguration = BuildConfiguration.Builder.newBuilder().name("test build config").buildScript("mvn deploy").build();
        BuildConfigurationAudited auditedBuildConfig = BuildConfigurationAudited.Builder.newBuilder().build();

        return BuildTask.build(
                buildConfiguration,
                auditedBuildConfig,
                false,
                false,
                MockUser.newTestUser(1),
                1,
                null,
                new Date(),
                buildConfiguration.getCurrentProductMilestone()
        );
    }

    private void addListener(KieSession session) {
        ProcessEventListener listener = new DefaultProcessEventListener() {
            @Override
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                long id = event.getProcessInstance().getId();
                String nodeName = event.getNodeInstance().getNodeName();
                log.info("received event for processId: {} beforeNodeTriggered: {}", id, nodeName);
            }

            @Override
            public void beforeNodeLeft(ProcessNodeLeftEvent event) {
                long id = event.getProcessInstance().getId();
                String nodeName = event.getNodeInstance().getNodeName();
                log.info("received event for processId: {} beforeNodeLeft: {}", id, nodeName);
            }
        };
        session.addEventListener(listener);
    }

    protected KieSession initKieSession(BpmModuleConfig bpmConfig) throws CoreException, ConfigurationParseException {


        RuntimeEngine restSessionFactory;
        try {
            restSessionFactory = RemoteRuntimeEngineFactory.newRestBuilder()
                    .addDeploymentId(bpmConfig.getDeploymentId())
                    .addUrl(new URL(bpmConfig.getBpmInstanceUrl()))
                    .addUserName(bpmConfig.getUsername())
                    .addPassword(bpmConfig.getPassword())
                    .addTimeout(5)
                    .build();
        } catch (Exception e) {
            throw new CoreException("Could not initialize connection to BPM server at '" +
                    bpmConfig.getBpmInstanceUrl() + "' check that the URL is correct.", e);
        }

        return restSessionFactory.getKieSession();
    }

}
