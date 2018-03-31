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
package org.jboss.pnc.coordinator.builder;

import org.jboss.pnc.common.Configuration;
import org.jboss.pnc.common.json.ConfigurationParseException;
import org.jboss.pnc.common.json.moduleconfig.SystemConfig;
import org.jboss.pnc.common.json.moduleprovider.PncConfigProvider;
import org.jboss.pnc.common.monitor.PullingMonitor;
import org.jboss.pnc.common.util.NamedThreadFactory;
import org.jboss.pnc.coordinator.BuildCoordinationException;
import org.jboss.pnc.coordinator.builder.datastore.DatastoreAdapter;
import org.jboss.pnc.dagscheduler.ResolutionStatus;
import org.jboss.pnc.dagscheduler.Status;
import org.jboss.pnc.dagscheduler.StatusUpdate;
import org.jboss.pnc.dagscheduler.DagResolver;
import org.jboss.pnc.dagscheduler.DefaultDagResolver;
import org.jboss.pnc.model.BuildConfigSetRecord;
import org.jboss.pnc.model.BuildConfiguration;
import org.jboss.pnc.model.BuildConfigurationSet;
import org.jboss.pnc.model.BuildStatus;
import org.jboss.pnc.model.User;
import org.jboss.pnc.spi.BuildCoordinationStatus;
import org.jboss.pnc.spi.BuildOptions;
import org.jboss.pnc.spi.BuildResult;
import org.jboss.pnc.spi.BuildSetStatus;
import org.jboss.pnc.spi.coordinator.BuildCoordinator;
import org.jboss.pnc.spi.coordinator.BuildSetTask;
import org.jboss.pnc.spi.coordinator.BuildTask;
import org.jboss.pnc.spi.coordinator.CompletionStatus;
import org.jboss.pnc.spi.coordinator.ProcessException;
import org.jboss.pnc.spi.coordinator.events.DefaultBuildSetStatusChangedEvent;
import org.jboss.pnc.spi.coordinator.events.DefaultBuildStatusChangedEvent;
import org.jboss.pnc.spi.datastore.DatastoreException;
import org.jboss.pnc.spi.events.BuildCoordinationStatusChangedEvent;
import org.jboss.pnc.spi.events.BuildSetStatusChangedEvent;
import org.jboss.pnc.spi.exception.BuildConflictException;
import org.jboss.pnc.spi.exception.CoreException;
import org.jboss.pnc.spi.executor.exceptions.ExecutorException;
import org.jboss.pnc.spi.repour.RepourResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 *
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2014-12-20.
 */
@ApplicationScoped
public class DefaultBuildCoordinator implements BuildCoordinator {

    private final Logger log = LoggerFactory.getLogger(DefaultBuildCoordinator.class);

    private Configuration configuration;
    private DatastoreAdapter datastoreAdapter;
    private Event<BuildCoordinationStatusChangedEvent> buildStatusChangedEventNotifier;
    private Event<BuildSetStatusChangedEvent> buildSetStatusChangedEventNotifier;

    private BuildScheduler buildScheduler;

//    private BuildQueue buildQueue;

    private BuildTasksInitializer buildTasksInitializer;

    private final DagResolver dagResolver = new DefaultDagResolver();
    private final BlockingQueue<BuildTask> readyTasksQueue = new LinkedBlockingQueue<>();
//    private final Set <BuildTask> runningTasks = new CopyOnWriteArraySet<>();
//    private final Set <BuildSetTask> runningSetTasks = new CopyOnWriteArraySet<>();
    BuildSetTaskRegistry buildSetTaskRegistry;
//    BuildTaskRegistry buildTaskRegistry;

    @Deprecated
    public DefaultBuildCoordinator(){} //workaround for CDI constructor parameter injection

    @Inject
    public DefaultBuildCoordinator(DatastoreAdapter datastoreAdapter, Event<BuildCoordinationStatusChangedEvent> buildStatusChangedEventNotifier,
                                   Event<BuildSetStatusChangedEvent> buildSetStatusChangedEventNotifier, BuildSchedulerFactory buildSchedulerFactory,
                                   BuildQueue buildQueue,
                                   Configuration configuration) {
        this.datastoreAdapter = datastoreAdapter;
        this.buildStatusChangedEventNotifier = buildStatusChangedEventNotifier;
        this.buildSetStatusChangedEventNotifier = buildSetStatusChangedEventNotifier;
        this.buildScheduler = buildSchedulerFactory.getBuildScheduler();
        this.configuration = configuration;
//        this.buildQueue = buildQueue;
        this.buildTasksInitializer = new BuildTasksInitializer(datastoreAdapter);

        dagResolver.setStatusUpdateListener((update) -> {
            if (update.getStatus().equals(Status.READY)) {
                readyTasksQueue.add(createBuildTask(update.getTaskId()));
            } else if (update.getStatus().equals(Status.READY)) {
                updateBuildTaskStatus(createBuildTask(update.getTaskId()), BuildCoordinationStatus.REJECTED,
                        "The configuration is already in the build queue.");

            } else if (update.getStatus().equals(Status.FAILED_DEPENDENCY)) {
                finishDueToFailedDependency(failedDependencyTask, createBuildTask(update.getTaskId()));
            }


        });
    }

    /**
     * Run a single build.  Uses the settings from the latest saved/audited build configuration.
     *
     * @param buildConfiguration The build configuration which will be used.  The latest version of this
     * build config will be built.
     * @param user The user who triggered the build.
     * @param buildOptions Customization of a build specified by user
     *
     * @return The new build task
     * @throws BuildConflictException If there is already a build running with the same build configuration Id and version
     */
    @Override
    public BuildSetTask build(BuildConfiguration buildConfiguration,
                           User user, BuildOptions buildOptions) throws BuildConflictException, CoreException {

        //TODO check if rebuild is required
        BuildSetTask buildSetTask =
                buildTasksInitializer.createBuildSetTask(
                        buildConfiguration,
                        buildOptions);

//        buildQueue.enqueueTaskSet(buildSetTask);
        Optional<StatusUpdate> statusUpdate = addTaskToResolver(createBuildTask(buildConfiguration.getId() + ""));
        if (statusUpdate.isPresent()) {
            throw new BuildConflictException("Rejected configuration BC.id:" + buildConfiguration.getId() + ". Reason: " + statusUpdate.get().getStatus());
        }


        return buildSetTask;
    }

    private BuildTask createBuildTask(String id) {
        return null; //TODO
    }

    private Integer buildRecordIdSupplier() {
        return datastoreAdapter.getNextBuildRecordId();
    }

    /**
     * Run a set of builds.  Only the current/latest version of builds in the given set will be executed.  The
     * order of execution is determined by the dependency relations between the build configurations.
     *
     * @param buildConfigurationSet The set of builds to be executed.
     * @param user The user who triggered the build.
     * @param buildOptions Customization of a build specified by user
     *
     * @return The new build set task
     * @throws CoreException Thrown if there is a problem initializing the build
     */
    @Override
    public BuildSetTask build(
            BuildConfigurationSet buildConfigurationSet,
            User user, BuildOptions buildOptions) throws CoreException {

        Set<BuildConfiguration> buildConfigurations = datastoreAdapter.getBuildConfigurations(buildConfigurationSet);

        BuildSetTask buildSetTask = buildTasksInitializer.createBuildSetTask(
                buildConfigurationSet,
                user,
                buildOptions,
                buildConfigurations);
        updateBuildSetTaskStatus(buildSetTask, BuildSetStatus.NEW);

        checkForEmptyBuildSetTask(buildSetTask);
        if (!buildOptions.isForceRebuild()) {
            checkIfAnyBuildConfigurationNeedsARebuild(buildSetTask, buildConfigurationSet);
        }

        scheduleTasks(buildSetTask);
        return buildSetTask;
    }

    private void checkIfAnyBuildConfigurationNeedsARebuild(BuildSetTask buildSetTask, BuildConfigurationSet buildConfigurationSet) {
        Set<BuildConfiguration> buildConfigurations = buildConfigurationSet.getBuildConfigurations();
        int requiresRebuild = buildConfigurations.size();
        for (BuildConfiguration buildConfiguration : buildConfigurations) {
            if (!datastoreAdapter.requiresRebuild(buildConfiguration)) {
                requiresRebuild--;
            }
        }
        if (requiresRebuild == 0) {
            updateBuildSetTaskStatus(buildSetTask, BuildSetStatus.REJECTED, "All build configs were previously built");
        }
    }

    private void scheduleTasks(BuildSetTask buildSetTask) {
        if (!BuildSetStatus.REJECTED.equals(buildSetTask.getStatus())) {
            buildSetTaskRegistry.add(buildSetTask);
            buildSetTask.getBuildTasks().stream()
//                    .filter(this::rejectAlreadySubmitted)
                    .forEach(this::addTaskToResolver);
        }
    }

    private Optional<StatusUpdate> addTaskToResolver(BuildTask buildTask) {

        BuildConfiguration buildConfiguration = buildTask.getBuildConfiguration();
        Set<String> dependencies = buildConfiguration.getDependencies().stream().map(bc -> bc.getId() + "").collect(Collectors.toSet());
        return dagResolver.submitTask(buildConfiguration.getId() + "", dependencies);
    }

    @Override
    public boolean cancel(int buildTaskId) throws CoreException {
        Optional<BuildTask> taskOptional = getSubmittedBuildTasks().stream()
                    .filter(buildTask -> buildTask.getId() == buildTaskId)
                    .findAny();
        if (taskOptional.isPresent()) {
            log.debug("Cancelling task {}.", taskOptional.get());
            try {
                boolean cancelSubmitted = buildScheduler.cancel(taskOptional.get());
                if (cancelSubmitted) {
                    monitorCancellation(taskOptional.get());
                } else {
                    cancelInternal(taskOptional.get());
                }
            } catch (CoreException e) {
                cancelInternal(taskOptional.get());
            }
            return true;
        } else {
            log.warn("Cannot find task {} to cancel.", buildTaskId);
            return false;
        }
    }

    private void monitorCancellation(BuildTask buildTask) {
        int cancellationTimeout = 30;
        PullingMonitor monitor = new PullingMonitor();

        Runnable invokeCancelInternal = () -> {
            if (!getSubmittedBuildTasks().contains(buildTask)) {
                log.debug("Task {} cancellation already completed.", buildTask.getId());
                return;
            }
            log.warn("Cancellation did not complete in {} seconds.", cancellationTimeout);
            cancelInternal(buildTask);
        };
        ScheduledFuture<?> timer = monitor.timer(invokeCancelInternal, cancellationTimeout, TimeUnit.SECONDS);
        //TODO optimization: cancel the timer when the task is canceled
        //timer.cancel(false);
    }

    private void cancelInternal(BuildTask buildTask) {

        BuildResult result = new BuildResult(
                CompletionStatus.CANCELLED,
                Optional.empty(),
                "", Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        completeBuild(buildTask, result);

        log.info("Task {} canceled internally.", buildTask.getId());
    }

    /**
     * Check if the given build set task is empty and update the status message appropriately
     */
    private void checkForEmptyBuildSetTask(BuildSetTask buildSetTask) {
        if (buildSetTask.getBuildTasks() == null || buildSetTask.getBuildTasks().isEmpty()) {
            updateBuildSetTaskStatus(buildSetTask, BuildSetStatus.REJECTED, "Build config set is empty");
        }
    }

    public void updateBuildTaskStatus(BuildTask task, BuildCoordinationStatus status){
        updateBuildTaskStatus(task, status, null);
    }

    private void updateBuildTaskStatus(BuildTask task, BuildCoordinationStatus status, String statusDescription){
        BuildCoordinationStatus oldStatus = task.getStatus();
        Integer userId = Optional.ofNullable(task.getUser()).map(User::getId).orElse(null);

        BuildCoordinationStatusChangedEvent buildStatusChanged = new DefaultBuildStatusChangedEvent(
                oldStatus,
                status,
                task.getId(),
                task.getBuildConfigurationAudited().getId(),
                task.getBuildConfigurationAudited().getRev(),
                task.getBuildConfigurationAudited().getName(),
                task.getStartTime(),
                task.getEndTime(),
                userId);
        log.debug("Updating build task {} status to {}", task.getId(), buildStatusChanged);
        if (status.isCompleted()) {
            markFinished(task, status, statusDescription);
        } else {
            task.setStatus(status);
            task.setStatusDescription(statusDescription);
        }
        buildStatusChangedEventNotifier.fire(buildStatusChanged);
        log.debug("Fired buildStatusChangedEventNotifier after task {} status update to {}.", task.getId(), status);
    }

    private void updateBuildSetTaskStatus(BuildSetTask buildSetTask, BuildSetStatus status) {
        updateBuildSetTaskStatus(buildSetTask, status, null);
    }

    private void updateBuildSetTaskStatus(BuildSetTask buildSetTask, BuildSetStatus status, String description) {
        log.debug("Setting new status {} on buildSetTask.id {}.", status, buildSetTask.getId());
        BuildSetStatus oldStatus = buildSetTask.getStatus();
        Optional<BuildConfigSetRecord> buildConfigSetRecord = buildSetTask.getBuildConfigSetRecord();
        sendSetStatusChangeEvent(buildSetTask, status, oldStatus, buildConfigSetRecord);
        
        // Rejected status needs to be propagated to the BuildConfigSetRecord in database. 
        // Completed BuildSets are updated using BuildSetTask#taskStatusUpdatedToFinalState()
        if( buildConfigSetRecord.isPresent() && BuildSetStatus.REJECTED.equals(status)) {
        	buildConfigSetRecord.get().setStatus(BuildStatus.REJECTED);
        	try {
        		datastoreAdapter.saveBuildConfigSetRecord(buildConfigSetRecord.get());
        	} catch (DatastoreException de) {
                log.warn("Failed to update build config set record to REJECTED status: " + de);
            }
        }
        
        buildSetTask.setStatus(status);
        buildSetTask.setStatusDescription(description);
    }

    private void sendSetStatusChangeEvent(BuildSetTask buildSetTask,
                                          BuildSetStatus status,
                                          BuildSetStatus oldStatus,
                                          Optional<BuildConfigSetRecord> maybeRecord) {
        maybeRecord.ifPresent(record -> {
            Integer userId = Optional.ofNullable(record.getUser()).map(User::getId).orElse(null);

            BuildSetStatusChangedEvent event = new DefaultBuildSetStatusChangedEvent(
                    oldStatus,
                    status,
                    buildSetTask.getId(),
                    record.getBuildConfigurationSet().getId(),
                    record.getBuildConfigurationSet().getName(),
                    record.getStartTime(),
                    record.getEndTime(),
                    userId);
            log.debug("Notifying build set status update {}.", event);
            buildSetStatusChangedEventNotifier.fire(event);
        });
    }

    private void processBuildTask(BuildTask task) {
        Consumer<BuildResult> onComplete = (result) ->  completeBuild(task, result);

        try {
            //check if task is already been build or is currently building
            //in case when task depends on two or more other tasks, all dependents call this method
            //process only tasks with status ENQUEUED
            synchronized (task) {
                if (task.getStatus() != BuildCoordinationStatus.ENQUEUED) {
                    log.debug("Skipping the execution of build task {} as it has been processed already. Status: {}.", task.getId(), task.getStatus());
                    return;
                }

                if (!task.getBuildOptions().isForceRebuild() && !datastoreAdapter.requiresRebuild(task)) {
                    updateBuildTaskStatus(task,
                            BuildCoordinationStatus.REJECTED_ALREADY_BUILT,
                            "The configuration has already been built");
                    return;
                }
                task.setStartTime(new Date());
                updateBuildTaskStatus(task, BuildCoordinationStatus.BUILDING);
            }
            buildScheduler.startBuilding(task, onComplete);
        } catch (CoreException | ExecutorException e) {
            log.debug(" Build coordination task failed. Setting it as SYSTEM_ERROR.", e);
            updateBuildTaskStatus(task, BuildCoordinationStatus.SYSTEM_ERROR, e.getMessage());
            try {
                datastoreAdapter.storeResult(task, Optional.empty(), e);
            } catch (DatastoreException e1) {
                log.error("Unable to store error [" + e.getMessage() + "] of build coordination task [" + task.getId() + "].", e1);
            }
        } catch (Error error) {
            log.error("Build coordination task failed with error! Setting it as SYSTEM_ERROR.", error);
            log.error("The system probably is in an invalid state!");
            updateBuildTaskStatus(task,BuildCoordinationStatus.SYSTEM_ERROR, error.getMessage());
            try {
                datastoreAdapter.storeResult(task, Optional.empty(), error);
            } catch (DatastoreException e1) {
                log.error("Unable to store error [" + error.getMessage() + "] of build coordination task [" + task.getId() + "].", e1);
            }
            throw error;
        }
    }

    public void completeBuild(BuildTask buildTask, BuildResult buildResult) {
        int buildTaskId = buildTask.getId();

        updateBuildTaskStatus(buildTask, BuildCoordinationStatus.BUILD_COMPLETED);

        BuildCoordinationStatus coordinationStatus = BuildCoordinationStatus.SYSTEM_ERROR;
        CompletionStatus operationCompletionStatus = buildResult.getCompletionStatus();
        try {
            if (buildResult.hasFailed()) {
                switch (operationCompletionStatus) {
                    case SYSTEM_ERROR:
                        ProcessException exception;
                        if (buildResult.getProcessException().isPresent()) {
                            exception = buildResult.getProcessException().get();
                            log.debug("[buildTaskId: {}] Storing build result with exception {}.",
                                    buildTaskId,
                                    exception.getMessage());
                        } else if (buildResult.getRepourResult().isPresent()) {
                            RepourResult repourResult = buildResult.getRepourResult().get();
                            if (repourResult.getCompletionStatus().isFailed()) {
                                exception = new ProcessException("Repour completed with system error.");
                                log.debug("[buildTaskId: {}] Storing build result with system error from repour: {}.",
                                        buildTaskId,
                                        repourResult.getLog());
                            } else {
                                exception = new ProcessException("Build completed with system error but no exception.");
                                log.error("[buildTaskId: {}] Storing build result with system_error and missing exception.", buildTaskId);
                            }
                        } else {
                            exception = new ProcessException("Build completed with system error but no exception and no Repour result.");
                            log.error("[buildTaskId: {}] Storing build result with system_error no exception and no Repour result.", buildTaskId);
                        }
                        datastoreAdapter.storeResult(buildTask, Optional.of(buildResult), exception);
                        coordinationStatus = BuildCoordinationStatus.SYSTEM_ERROR;
                        break;

                    case CANCELLED:
                    case TIMED_OUT:
                        log.debug("[buildTaskId: {}] Storing failed build result. FailedReasonStatus: {}", buildTaskId, operationCompletionStatus);
                        datastoreAdapter.storeResult(buildTask, buildResult);
                        coordinationStatus = BuildCoordinationStatus.CANCELLED;
                        break;

                    case FAILED:
                        log.debug("[buildTaskId: {}] Storing failed build result. FailedReasonStatus: {}", buildTaskId, operationCompletionStatus);
                        datastoreAdapter.storeResult(buildTask, buildResult);
                        coordinationStatus = BuildCoordinationStatus.DONE_WITH_ERRORS;
                        break;

                    case SUCCESS:
                        throw new BuildCoordinationException("Failed task with SUCCESS completion status ?!.");
                }

            } else {
                log.debug("[buildTaskId: {}] Storing success build result.", buildTaskId);
                datastoreAdapter.storeResult(buildTask, buildResult);
                coordinationStatus = BuildCoordinationStatus.DONE;
            }
        } catch (Throwable e ) {
            log.error("Cannot store results to datastore.", e);
            coordinationStatus = BuildCoordinationStatus.SYSTEM_ERROR;
        } finally {
            dagResolver.resolveTask(buildTaskId + "", fromCompletionStatus(operationCompletionStatus));
            updateBuildTaskStatus(buildTask, coordinationStatus);
        }
    }

    private ResolutionStatus fromCompletionStatus(CompletionStatus operationCompletionStatus) {
        if (operationCompletionStatus == null) {
            log.error("Cannot convert to ResolutionStatus operationCompletionStatus is null.");
            return ResolutionStatus.SYSTEM_ERROR;
        }
        switch (operationCompletionStatus) {
            case SUCCESS:
                return ResolutionStatus.SUCCESS;
            case FAILED:
                return ResolutionStatus.FAILED;
            case CANCELLED:
                return ResolutionStatus.CANCELLED;
            case TIMED_OUT:
                return ResolutionStatus.TIME_OUT;
            case SYSTEM_ERROR:
                return ResolutionStatus.SYSTEM_ERROR;
        }
        log.error("Cannot convert to ResolutionStatus unknown operationCompletionStatus.");
        return ResolutionStatus.SYSTEM_ERROR;
    }

    private synchronized void markFinished(BuildTask task, BuildCoordinationStatus status, String statusDescription) {
        BuildSetTask buildSetTask = task.getBuildSetTask();
        if (buildSetTask != null && isFinished(buildSetTask)) {
            completeBuildSetTask(buildSetTask);
        }
    }

    private boolean isFinished(BuildSetTask buildSetTask) {
        return buildSetTask
                .getBuildTasks()
                .stream()
                .allMatch(t -> t.getStatus().isCompleted());
    }

//    private void handleErroneousFinish(BuildTask failedTask) {
//        BuildSetTask taskSet = failedTask.getBuildSetTask();
//        if (taskSet != null) {
//            log.debug("Finishing tasks in set {}, after failedTask {}.", taskSet, failedTask);
//            taskSet.getBuildTasks().stream()
//                    .filter(t -> isDependentOn(failedTask, t))
//                    .filter(t -> !t.getStatus().isCompleted())
//                    .forEach(t -> );
//        }
//    }

    private void storeRejectedTask(BuildTask buildTask) {
        try {
            log.debug("Storing rejected task {}", buildTask);
            datastoreAdapter.storeRejected(buildTask);
        } catch (DatastoreException e) {
            log.error("Unable to store rejected task.", e);
        }
    }

    private void completeBuildSetTask(BuildSetTask buildSetTask) {
        log.debug("Completing buildSetTask {} ...", buildSetTask);
        buildSetTaskRegistry.remove(buildSetTask);
        buildSetTask.taskStatusUpdatedToFinalState();
        updateBuildSetTaskStatus(buildSetTask, BuildSetStatus.DONE);

        buildSetTask.getBuildConfigSetRecord().ifPresent(r -> {
                    try {
                        datastoreAdapter.saveBuildConfigSetRecord(r);
                    } catch (DatastoreException e) {
                        log.error("Unable to save build config set record", e);
                    }
                }
        );
    }

    private void finishDueToFailedDependency(BuildTask failedTask, BuildTask dependentTask) {
        log.debug("Finishing task {} due ta failed dependency.", dependentTask);
//        buildQueue.removeTask(dependentTask);
        updateBuildTaskStatus(dependentTask, BuildCoordinationStatus.REJECTED_FAILED_DEPENDENCIES,
                "Dependent build " + failedTask.getBuildConfiguration().getName() + " failed.");
        log.trace("Status of build task {} updated.", dependentTask);
        storeRejectedTask(dependentTask);
    }

    public List<BuildTask> getSubmittedBuildTasks() {
        return dagResolver.getAll();
    }

    @PostConstruct
    public void start() {
        startThreads();
    }

    private void startThreads() {
        int threadPoolSize = 1;
        try {
            SystemConfig systemConfig = configuration.getModuleConfig(new PncConfigProvider<>(SystemConfig.class));
            threadPoolSize = systemConfig.getCoordinatorThreadPoolSize();
        } catch (ConfigurationParseException e) {
            log.error("Error parsing configuration. Will set BuildCoordinator.threadPoolSize to {}", threadPoolSize, e);
        }
        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize, new NamedThreadFactory("build-coordinator"));
        for (int i = 0; i < threadPoolSize; i++) {
            executorService.execute(this::takeAndProcessTask);
        }
    }

    private void takeAndProcessTask() {
        while (true) {
            try {
                BuildTask task = readyTasksQueue.take(); //TODO limit max concurrent builds
                processBuildTask(task);
                log.info("Build task: " + task + ", will pick up next task");
            } catch (InterruptedException e) {
                log.warn("BuildCoordinator thread interrupted. Possibly the system is being shut down", e);
                break;
            }
        }
    }
}
