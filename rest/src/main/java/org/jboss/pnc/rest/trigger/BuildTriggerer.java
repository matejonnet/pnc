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
package org.jboss.pnc.rest.trigger;

import com.google.common.base.Preconditions;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;

import org.jboss.logging.Logger;
import org.jboss.pnc.common.Configuration;
import org.jboss.pnc.common.json.ConfigurationParseException;
import org.jboss.pnc.common.json.moduleconfig.BpmModuleConfig;
import org.jboss.pnc.common.json.moduleprovider.PncConfigProvider;
import org.jboss.pnc.common.util.HttpUtils;
import org.jboss.pnc.core.builder.BuildSetCoordinator;
import org.jboss.pnc.core.exception.CoreException;
import org.jboss.pnc.core.notifications.buildSetTask.BuildSetCallBack;
import org.jboss.pnc.core.notifications.buildSetTask.BuildSetStatusNotifications;
import org.jboss.pnc.core.notifications.buildTask.BuildCallBack;
import org.jboss.pnc.core.notifications.buildTask.BuildStatusNotifications;
import org.jboss.pnc.model.*;
import org.jboss.pnc.spi.builddriver.exception.BuildDriverException;
import org.jboss.pnc.spi.datastore.DatastoreException;
import org.jboss.pnc.spi.datastore.repositories.BuildConfigurationAuditedRepository;
import org.jboss.pnc.spi.datastore.repositories.BuildConfigurationRepository;
import org.jboss.pnc.spi.datastore.repositories.BuildConfigurationSetRepository;
import org.jboss.pnc.spi.datastore.repositories.SortInfoProducer;
import org.jboss.pnc.spi.events.BuildSetStatusChangedEvent;
import org.jboss.pnc.spi.events.BuildStatusChangedEvent;
import org.jboss.pnc.spi.repositorymanager.RepositoryManagerException;

import javax.ejb.Stateless;
import javax.inject.Inject;

import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;

@Stateless
public class BuildTriggerer {

    private final Logger log = Logger.getLogger(BuildTriggerer.class);
    
    private BuildSetCoordinator buildCoordinator;
    private BuildConfigurationRepository buildConfigurationRepository;
    private BuildConfigurationAuditedRepository buildConfigurationAuditedRepository;
    private BuildConfigurationSetRepository buildConfigurationSetRepository;
    private BuildSetStatusNotifications buildSetStatusNotifications;
    private BuildStatusNotifications buildStatusNotifications;

    private SortInfoProducer sortInfoProducer;

    private BpmModuleConfig bpmConfig = null;
    
    @Deprecated //not meant for usage its only to make CDI happy
    public BuildTriggerer() {
    }

    @Inject
    public BuildTriggerer(final BuildSetCoordinator buildCoordinator, final BuildConfigurationRepository buildConfigurationRepository,
            final BuildConfigurationAuditedRepository buildConfigurationAuditedRepository, final BuildConfigurationSetRepository buildConfigurationSetRepository,
            BuildSetStatusNotifications buildSetStatusNotifications, BuildStatusNotifications buildStatusNotifications,
            SortInfoProducer sortInfoProducer) {
        this.buildCoordinator = buildCoordinator;
        this.buildConfigurationRepository = buildConfigurationRepository;
        this.buildConfigurationAuditedRepository = buildConfigurationAuditedRepository;
        this.buildConfigurationSetRepository= buildConfigurationSetRepository;
        this.buildSetStatusNotifications = buildSetStatusNotifications;
        this.buildStatusNotifications = buildStatusNotifications;
        this.sortInfoProducer = sortInfoProducer;
    }

    public int triggerBuilds( final Integer buildConfigurationId, User currentUser, URL callBackUrl)
            throws InterruptedException, CoreException, BuildDriverException, RepositoryManagerException, DatastoreException
    {
        Consumer<BuildStatusChangedEvent> onStatusUpdate = (statusChangedEvent) -> {
            if(statusChangedEvent.getNewStatus().isCompleted()) {
                // Expecting URL like: http://host:port/business-central/rest/runtime/org.test:Test1:1.0/process/instance/7/signal?signal=testSig
                signalBpmEvent(callBackUrl.toString() + "&event=" + statusChangedEvent.getNewStatus());
            }
        };

        int buildTaskId = triggerBuilds(buildConfigurationId, currentUser);
        buildStatusNotifications.subscribe(new BuildCallBack(buildTaskId, onStatusUpdate));
        return buildTaskId;
    }

    public int triggerBuilds( final Integer configurationId, User currentUser )
        throws InterruptedException, CoreException, BuildDriverException, RepositoryManagerException, DatastoreException
    {
        final BuildConfiguration configuration = buildConfigurationRepository.queryById(configurationId);
        Preconditions.checkArgument(configuration != null, "Can't find configuration with given id=" + configurationId);

        final BuildRecordSet buildRecordSet = new BuildRecordSet();
        if (configuration.getProductVersions() != null  && !configuration.getProductVersions().isEmpty()) {
            ProductVersion productVersion = configuration.getProductVersions().iterator().next();
            buildRecordSet.setPerformedInProductMilestone(productVersion.getCurrentProductMilestone());
        }

        Integer taskId = buildCoordinator.build(configuration, currentUser).getId();
        return taskId;
    }

    public int triggerBuildConfigurationSet( final Integer buildConfigurationSetId, User currentUser, URL callBackUrl)
        throws InterruptedException, CoreException, BuildDriverException, RepositoryManagerException, DatastoreException
    {
        Consumer<BuildSetStatusChangedEvent> onStatusUpdate = (statusChangedEvent) -> {
            if(statusChangedEvent.getNewStatus().isCompleted()) {
                // Expecting URL like: http://host:port/business-central/rest/runtime/org.test:Test1:1.0/process/instance/7/signal?signal=testSig
                signalBpmEvent(callBackUrl.toString() + "&event=" + statusChangedEvent.getNewStatus());
            }
        };

        int buildSetTaskId = triggerBuildConfigurationSet(buildConfigurationSetId, currentUser);
        buildSetStatusNotifications.subscribe(new BuildSetCallBack(buildSetTaskId, onStatusUpdate));
        return buildSetTaskId;
    }

    public int triggerBuildConfigurationSet( final Integer buildConfigurationSetId, User currentUser )
        throws InterruptedException, CoreException, BuildDriverException, RepositoryManagerException, DatastoreException
    {
        final BuildConfigurationSet buildConfigurationSet = buildConfigurationSetRepository.queryById(buildConfigurationSetId);
        Preconditions.checkArgument(buildConfigurationSet != null, "Can't find configuration with given id=" + buildConfigurationSetId);

        return buildCoordinator.build(buildConfigurationSet, currentUser).getId();
    }

    private void signalBpmEvent(String uri) {
        if (bpmConfig == null) {
            try {
                bpmConfig = new Configuration()
                        .getModuleConfig(new PncConfigProvider<BpmModuleConfig>(BpmModuleConfig.class));
            } catch (ConfigurationParseException e) {
                log.error("Error parsing BPM config.", e);
            }
        }

        HttpPost request = new HttpPost(uri);
        request.addHeader("Authorization", getAuthHeader());
        log.info("Executing request " + request.getRequestLine());

        try (CloseableHttpClient httpClient = HttpUtils.getPermissiveHttpClient()) {
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                log.info(response.getStatusLine());
            }
        } catch (IOException e) {
            log.error("Error occured executing the callback.", e);
        }
    }

    private String getAuthHeader() {
        byte[] encodedBytes = Base64.encodeBase64((bpmConfig.getUsername() + ":" + bpmConfig.getPassword()).getBytes());
        return "Basic " + new String(encodedBytes);
    }
}
