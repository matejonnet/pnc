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
package org.jboss.pnc.core.builder;

import org.jboss.pnc.common.util.ResultWrapper;
import org.jboss.pnc.common.util.StreamCollectors;
import org.jboss.pnc.core.BuildDriverFactory;
import org.jboss.pnc.core.EnvironmentDriverFactory;
import org.jboss.pnc.core.RepositoryManagerFactory;
import org.jboss.pnc.core.content.ContentIdentityManager;
import org.jboss.pnc.core.exception.CoreException;
import org.jboss.pnc.model.BuildConfigSetRecord;
import org.jboss.pnc.model.BuildConfiguration;
import org.jboss.pnc.model.BuildConfigurationSet;
import org.jboss.pnc.model.ProductMilestone;
import org.jboss.pnc.model.User;
import org.jboss.pnc.spi.BuildExecutionType;
import org.jboss.pnc.spi.BuildSetStatus;
import org.jboss.pnc.spi.BuildStatus;
import org.jboss.pnc.spi.datastore.DatastoreException;
import org.jboss.pnc.spi.events.BuildSetStatusChangedEvent;
import org.jboss.pnc.spi.events.BuildStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 *
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2014-12-20.
 */
@ApplicationScoped
public class DefaultBuildSetCoordinator implements BuildSetCoordinator {

    private Logger log = LoggerFactory.getLogger(DefaultBuildSetCoordinator.class);
    private Queue<BuildTask> buildTasks = new ConcurrentLinkedQueue<>(); //TODO garbage collector (time-out, error state)


    private ExecutorService dbexecutorSingleThread = Executors.newFixedThreadPool(1);

    private DatastoreAdapter datastoreAdapter;
    private Event<BuildStatusChangedEvent> buildStatusChangedEventNotifier;
    private Event<BuildSetStatusChangedEvent> buildSetStatusChangedEventNotifier;
    private BuildProcessManager buildProcessManager;

    @Deprecated
    public DefaultBuildSetCoordinator(){} //workaround for CDI constructor parameter injection

    @Inject
    public DefaultBuildSetCoordinator(BuildDriverFactory buildDriverFactory, RepositoryManagerFactory repositoryManagerFactory,
                                      EnvironmentDriverFactory environmentDriverFactory, DatastoreAdapter datastoreAdapter,
                                      Event<BuildStatusChangedEvent> buildStatusChangedEventNotifier,
                                      Event<BuildSetStatusChangedEvent> buildSetStatusChangedEventNotifier, BuildProcessManager buildProcessManager) {
        this.datastoreAdapter = datastoreAdapter;
        this.buildStatusChangedEventNotifier = buildStatusChangedEventNotifier;
        this.buildSetStatusChangedEventNotifier = buildSetStatusChangedEventNotifier;
        this.buildProcessManager = buildProcessManager;
    }

    /**
     * Trigger single build which is a part of given set.
     * Note that dependency resolution is not checked, the system will run the build and expect that dependencies are available.
     *
     * @param buildConfiguration
     * @param userTriggeredBuild
     * @return
     * @throws CoreException
     */
    @Override
    public BuildTask buildSingleConfiguration(BuildConfiguration buildConfiguration, BuildConfigSetRecord buildConfigSetRecord, User userTriggeredBuild) throws CoreException {
        BuildSetTask buildSetTask = new BuildSetTask( //TODO improve: remove dependency to a BuildSetTask
                this,
                buildConfigSetRecord,
                BuildExecutionType.COMPOSED_BUILD,
                getProductMilestone(buildConfigSetRecord.getBuildConfigurationSet()));

        BuildTask buildTask = initializeBuildTask(buildConfiguration, buildSetTask);

        if (!rejectAlreadySubmitted(buildTask)) {
            buildSetTask.addBuildTask(buildTask); //probably not required
            Runnable onComplete = () -> buildTasks.remove(buildTask);
            buildProcessManager.processBuildTask(buildTask, onComplete);
        }

        return buildTask;
    }

    /**
     * Trigger a build of individual standalone (not part of any set) build configuration
     * @param buildConfiguration
     * @param userTriggeredBuild
     * @return
     * @throws CoreException
     */
    @Override
    public BuildTask build(BuildConfiguration buildConfiguration, User userTriggeredBuild) throws CoreException {
        BuildConfigurationSet buildConfigurationSet = BuildConfigurationSet.Builder.newBuilder()
                .name(buildConfiguration.getName())
                .buildConfiguration(buildConfiguration)
                .build();

        BuildSetTask buildSetTask = createBuildSetTask(buildConfigurationSet, userTriggeredBuild, BuildExecutionType.STANDALONE_BUILD);

        scheduleBuilds(buildSetTask);
        BuildTask buildTask = buildSetTask.getBuildTasks().stream().collect(StreamCollectors.singletonCollector());
        return buildTask;
    }

    @Override
    public BuildSetTask buildSet(BuildConfigurationSet buildConfigurationSet, User userTriggeredBuild) throws CoreException, DatastoreException {

        BuildSetTask buildSetTask = createBuildSetTask(buildConfigurationSet, userTriggeredBuild, BuildExecutionType.COMPOSED_BUILD);

        scheduleBuilds(buildSetTask);
        return buildSetTask;
    }

    private BuildSetTask createBuildSetTask(BuildConfigurationSet buildConfigurationSet, User user, BuildExecutionType buildType) throws CoreException {
        BuildConfigSetRecord buildConfigSetRecord = BuildConfigSetRecord.Builder.newBuilder()
                .buildConfigurationSet(buildConfigurationSet)
                .user(user)
                .startTime(new Date())
                .status(org.jboss.pnc.model.BuildStatus.BUILDING)
                .build();

        if (BuildExecutionType.COMPOSED_BUILD.equals(buildType)) {
            buildConfigSetRecord = this.saveBuildConfigSetRecord(buildConfigSetRecord);
        }

        BuildSetTask buildSetTask = new BuildSetTask(
                this,
                buildConfigSetRecord,
                buildType,
                getProductMilestone(buildConfigurationSet));

        initializeBuildTasksInSet(buildSetTask);
        return buildSetTask;
    }

    /**
     * Creates build tasks and sets up the appropriate dependency relations
     * 
     * @param buildSetTask The build set task which will contain the build tasks.  This must already have
     * initialized the BuildConfigSet, BuildConfigSetRecord, Milestone, etc.
     */
    private void initializeBuildTasksInSet(BuildSetTask buildSetTask) {
        // Loop to create the build tasks
        for(BuildConfiguration buildConfig : buildSetTask.getBuildConfigurationSet().getBuildConfigurations()) {
            BuildTask buildTask = initializeBuildTask(buildConfig, buildSetTask);
            buildSetTask.addBuildTask(buildTask);
        }

        // Loop again to set dependencies
        for (BuildTask buildTask : buildSetTask.getBuildTasks()) {
            for (BuildConfiguration dep : buildTask.getBuildConfigurationDependencies()) {
                if (buildSetTask.getBuildConfigurationSet().getBuildConfigurations().contains(dep)) {
                    BuildTask depTask = buildSetTask.getBuildTask(dep);
                    if (depTask != null) {
                        buildTask.addDependency(depTask);
                    }
                }
            }
        }
    }

    private BuildTask initializeBuildTask(BuildConfiguration buildConfig, BuildSetTask buildSetTask) {
        BuildConfigurationSet buildConfigurationSet = buildSetTask.getBuildConfigurationSet();
        String topContentId = ContentIdentityManager.getProductContentId(buildConfigurationSet.getProductVersion());
        String buildSetContentId = ContentIdentityManager.getBuildSetContentId(buildConfigurationSet);
        String buildContentId = ContentIdentityManager.getBuildContentId(buildConfig);

        return new BuildTask(
                this,
                buildConfig,
                datastoreAdapter.getLatestBuildConfigurationAudited(buildConfig.getId()),
                topContentId,
                buildSetContentId,
                buildContentId,
                buildSetTask.getBuildTaskType(),
                buildSetTask.getBuildConfigSetRecord().getUser(), //TODO use user who triggered individual configuration
                buildSetTask,
                datastoreAdapter.getNextBuildRecordId());
    }

    /**
     * Get the product milestone (if any) associated with this build config set.
     * @param buildConfigSet
     * @return The product milestone, or null if there is none
     */
    private ProductMilestone getProductMilestone(BuildConfigurationSet buildConfigSet) {
        if(buildConfigSet.getProductVersion() == null || buildConfigSet.getProductVersion().getCurrentProductMilestone() == null) {
            return null;
        }
        return buildConfigSet.getProductVersion().getCurrentProductMilestone();
    }

    private void scheduleBuilds(BuildSetTask buildSetTask) throws CoreException {

        Predicate<BuildTask> readyToBuild = (buildTask) -> {
            return buildTask.readyToBuild();
        };

        if (!BuildSetStatus.REJECTED.equals(buildSetTask.getStatus())) {
            buildSetTask.getBuildTasks().stream()
                    .filter(readyToBuild)
                    .filter(bt -> rejectAlreadySubmitted(bt))
                    .forEach(buildTask -> fireBuild(buildTask));
        }
    }

    void fireBuild(BuildTask buildTask) {
        Runnable onComplete = () -> buildTasks.remove(buildTask);
        if (true) { //TODO if using BPM
            buildProcessManager.processBuildTask(buildTask, onComplete);
        } else {
            //start remote bpm process
        }
    };



    /**
     * Save the build config set record using a single thread for all db operations.
     * This ensures that database operations are done in the correct sequence, for example
     * in the case of a build config set.
     *
     * @param buildConfigSetRecord
     * @return
     */
    @Override
    public BuildConfigSetRecord saveBuildConfigSetRecord(BuildConfigSetRecord buildConfigSetRecord) throws CoreException {
        ResultWrapper<BuildConfigSetRecord, DatastoreException> result = null;
        try {
            result = CompletableFuture.supplyAsync(() -> this.saveBuildConfigSetRecordInternal(buildConfigSetRecord), dbexecutorSingleThread).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new CoreException(e);
        }
        if (result.getException() != null) {
            throw new CoreException(result.getException());
        } else {
            return result.getResult();
        }
    }

    /**
     * Save the build config set record to the database.  The result wrapper will contain an exception
     * if there is a problem while saving.
     */
    private ResultWrapper<BuildConfigSetRecord, DatastoreException> saveBuildConfigSetRecordInternal(
            BuildConfigSetRecord buildConfigSetRecord) {

        try {
            buildConfigSetRecord = datastoreAdapter.saveBuildConfigSetRecord(buildConfigSetRecord);
            return new ResultWrapper<>(buildConfigSetRecord);
        } catch (DatastoreException e) {
            log.error("Unable to save build config set record", e);
            return new ResultWrapper<>(buildConfigSetRecord, e);
        }
    }

    @Override
    public List<BuildTask> getBuildTasks() {
        return Collections.unmodifiableList(buildTasks.stream().collect(Collectors.toList()));
    }

    private boolean rejectAlreadySubmitted(BuildTask buildTask) {
        boolean alreadySubmitted = buildTasks.contains(buildTask);
        if (alreadySubmitted) {
            buildTask.setStatus(BuildStatus.REJECTED);
            buildTask.setStatusDescription("The configuration is already in the build queue.");
        }
        return alreadySubmitted;
    }

    Event<BuildStatusChangedEvent> getBuildStatusChangedEventNotifier() {
        return buildStatusChangedEventNotifier;
    }

    Event<BuildSetStatusChangedEvent> getBuildSetStatusChangedEventNotifier() {
        return buildSetStatusChangedEventNotifier;
    }

    @Override
    public void shutdownCoordinator(){
        buildProcessManager.shutdown();
    }

}
