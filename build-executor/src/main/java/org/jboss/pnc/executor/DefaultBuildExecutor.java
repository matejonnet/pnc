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

package org.jboss.pnc.executor;

import org.jboss.pnc.common.Configuration;
import org.jboss.pnc.common.json.ConfigurationParseException;
import org.jboss.pnc.common.json.moduleconfig.SystemConfig;
import org.jboss.pnc.common.json.moduleprovider.PncConfigProvider;
import org.jboss.pnc.common.util.NamedThreadFactory;
import org.jboss.pnc.common.util.StringUtils;
import org.jboss.pnc.executor.exceptions.BuildProcessException;
import org.jboss.pnc.executor.servicefactories.BuildDriverFactory;
import org.jboss.pnc.executor.servicefactories.EnvironmentDriverFactory;
import org.jboss.pnc.executor.servicefactories.RepositoryManagerFactory;
import org.jboss.pnc.logging.OperationLogger;
import org.jboss.pnc.logging.OperationLoggerFactory;
import org.jboss.pnc.model.BuildStatus;
import org.jboss.pnc.model.BuildType;
import org.jboss.pnc.model.TargetRepository;
import org.jboss.pnc.spi.BuildExecutionStatus;
import org.jboss.pnc.spi.builddriver.BuildDriver;
import org.jboss.pnc.spi.builddriver.BuildDriverResult;
import org.jboss.pnc.spi.builddriver.CompletedBuild;
import org.jboss.pnc.spi.builddriver.DebugData;
import org.jboss.pnc.spi.builddriver.RunningBuild;
import org.jboss.pnc.spi.environment.DestroyableEnvironment;
import org.jboss.pnc.spi.environment.EnvironmentDriver;
import org.jboss.pnc.spi.environment.RunningEnvironment;
import org.jboss.pnc.spi.environment.StartedEnvironment;
import org.jboss.pnc.spi.environment.exception.EnvironmentDriverException;
import org.jboss.pnc.spi.events.BuildExecutionStatusChangedEvent;
import org.jboss.pnc.spi.executor.BuildExecutionConfiguration;
import org.jboss.pnc.spi.executor.BuildExecutionSession;
import org.jboss.pnc.spi.executor.BuildExecutor;
import org.jboss.pnc.spi.executor.exceptions.ExecutorException;
import org.jboss.pnc.spi.repositorymanager.BuildExecution;
import org.jboss.pnc.spi.repositorymanager.RepositoryManager;
import org.jboss.pnc.spi.repositorymanager.RepositoryManagerResult;
import org.jboss.pnc.spi.repositorymanager.model.RepositorySession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@ApplicationScoped
public class DefaultBuildExecutor implements BuildExecutor {

    private final Logger log = LoggerFactory.getLogger(DefaultBuildExecutor.class);
    private final OperationLogger operationLogger = OperationLoggerFactory.getLogger("build-executor");

    private ExecutorService executor;

    private RepositoryManagerFactory repositoryManagerFactory;
    private BuildDriverFactory buildDriverFactory;
    private EnvironmentDriverFactory environmentDriverFactory;
    private final Map<Integer, DefaultBuildExecutionSession> runningExecutions = new HashMap<>();

    private SystemConfig systemConfig;

    @Deprecated
    public DefaultBuildExecutor() {} //CDI workaround for constructor injection

    @Inject
    public DefaultBuildExecutor(
            RepositoryManagerFactory repositoryManagerFactory,
            BuildDriverFactory buildDriverFactory,
            EnvironmentDriverFactory environmentDriverFactory,
            Configuration configuration) {

        this.repositoryManagerFactory = repositoryManagerFactory;
        this.buildDriverFactory = buildDriverFactory;
        this.environmentDriverFactory = environmentDriverFactory;

        int executorThreadPoolSize = 12;
        try {
            systemConfig = configuration.getModuleConfig(new PncConfigProvider<>(SystemConfig.class));
            String executorThreadPoolSizeStr = systemConfig.getExecutorThreadPoolSize();
            if (executorThreadPoolSizeStr != null) {
                executorThreadPoolSize = Integer.parseInt(executorThreadPoolSizeStr);
            }
        } catch (ConfigurationParseException e) {
            log.warn("Unable parse config. Using defaults.");
        }

        executor = Executors.newFixedThreadPool(executorThreadPoolSize, new NamedThreadFactory("default-build-executor"));
    }


    @Override
    public BuildExecutionSession startBuilding(
            BuildExecutionConfiguration buildExecutionConfiguration,
            Consumer<BuildExecutionStatusChangedEvent> onBuildExecutionStatusChangedEvent,
            String accessToken) throws ExecutorException {

        DefaultBuildExecutionSession buildExecutionSession = new DefaultBuildExecutionSession(buildExecutionConfiguration, onBuildExecutionStatusChangedEvent);
        runningExecutions.put(buildExecutionConfiguration.getId(), buildExecutionSession);

        buildExecutionSession.setStartTime(new Date());

        operationLogger.info(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Starting build execution...");

        buildExecutionSession.setStatus(BuildExecutionStatus.NEW);
        buildExecutionSession.setAccessToken(accessToken);

        DebugData debugData = new DebugData(buildExecutionConfiguration.isPodKeptOnFailure());

        CompletableFuture.supplyAsync(() -> configureRepository(buildExecutionSession), executor)
                .thenApplyAsync(repositoryConfiguration -> setUpEnvironment(buildExecutionSession, repositoryConfiguration, debugData), executor)
                .thenComposeAsync(startedEnvironment -> waitForEnvironmentInitialization(buildExecutionSession, startedEnvironment), executor)
                .thenComposeAsync(runningBuild -> runTheBuild(buildExecutionSession), executor)
                //no cancellation after this point
                .thenApplyAsync(completedBuild -> optionallyEnableSsh(buildExecutionSession, completedBuild), executor)
                .thenApplyAsync(completedBuild -> retrieveBuildDriverResults(buildExecutionSession, completedBuild), executor)
                .thenApplyAsync(nul -> retrieveRepositoryManagerResults(buildExecutionSession), executor)
                .handleAsync((nul, e) -> completeExecution(buildExecutionSession, e), executor);

        //TODO re-connect running instances in case of crash
        return buildExecutionSession;
    }

    @Override
    public void cancel(Integer executionConfigurationId) throws ExecutorException {
        DefaultBuildExecutionSession buildExecutionSession = runningExecutions.get(executionConfigurationId);
        if (buildExecutionSession != null) {
            log.info("Cancelling build {}.", buildExecutionSession.getId());
            buildExecutionSession.cancel();
        } else {
            log.warn("Trying to cancel non existing session.");
        }
    }

    private CompletedBuild optionallyEnableSsh(BuildExecutionSession session, CompletedBuild completedBuild) {
        RunningEnvironment runningEnvironment = session.getRunningEnvironment();
        if (runningEnvironment != null) {
            DebugData debugData = runningEnvironment.getDebugData();
            if (debugData.isDebugEnabled()) {
                debugData.getSshServiceInitializer().accept(debugData);
            }
        }
        return completedBuild;
    }

    @Override
    public BuildExecutionSession getRunningExecution(int buildExecutionTaskId) {
        return runningExecutions.get(buildExecutionTaskId);
    }

    private RepositorySession configureRepository(DefaultBuildExecutionSession buildExecutionSession) {
        if (buildExecutionSession.isCanceled()) {
            return null;
        }
        BuildExecutionConfiguration buildExecutionConfiguration = buildExecutionSession.getBuildExecutionConfiguration();
        operationLogger.info(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Setting up repository...");
        buildExecutionSession.setStatus(BuildExecutionStatus.REPO_SETTING_UP);

        BuildType buildType = buildExecutionConfiguration.getBuildType();
        if (buildType == null) {
            throw new BuildProcessException("Missing required value buildExecutionConfiguration.buildType");
        }
        TargetRepository.Type repositoryType = BuildTypeToRepositoryType.getRepositoryType(buildType);

        try {
            RepositoryManager repositoryManager = repositoryManagerFactory.getRepositoryManager(repositoryType);
            BuildExecution buildExecution = buildExecutionConfiguration;
            return repositoryManager.createBuildRepository(buildExecution, buildExecutionSession.getAccessToken(), repositoryType);
        } catch (Throwable e) {
            throw new BuildProcessException(e);
        }
    }

    private StartedEnvironment setUpEnvironment(
            DefaultBuildExecutionSession buildExecutionSession,
            RepositorySession repositorySession,
            DebugData debugData) {

        if (buildExecutionSession.isCanceled()) {
            return null;
        }
        BuildExecutionConfiguration buildExecutionConfiguration = buildExecutionSession.getBuildExecutionConfiguration();
        operationLogger.info(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Setting up build environment ...");
        buildExecutionSession.setStatus(BuildExecutionStatus.BUILD_ENV_SETTING_UP);
        try {
            EnvironmentDriver envDriver = environmentDriverFactory.getDriver(buildExecutionConfiguration.getSystemImageType());
            StartedEnvironment startedEnv = envDriver.startEnvironment(
                    buildExecutionConfiguration.getSystemImageId(),
                    buildExecutionConfiguration.getSystemImageRepositoryUrl(),
                    buildExecutionConfiguration.getSystemImageType(),
                    repositorySession,
                    debugData,
                    buildExecutionSession.getAccessToken());

            buildExecutionSession.setCancelHook(() -> startedEnv.cancel());

            return startedEnv;
        } catch (Throwable e) {
            throw new BuildProcessException(e);
        }
    }

    private CompletableFuture<Void> waitForEnvironmentInitialization(
            DefaultBuildExecutionSession buildExecutionSession, StartedEnvironment startedEnvironment) {

        CompletableFuture<Void> waitToCompleteFuture = new CompletableFuture<>();

        if (buildExecutionSession.isCanceled()) {
            waitToCompleteFuture.complete(null);
            return waitToCompleteFuture;
        }

        try {
            BuildExecutionConfiguration buildExecutionConfiguration = buildExecutionSession.getBuildExecutionConfiguration();

            Consumer<RunningEnvironment> onComplete = (runningEnvironment) -> {
                operationLogger.info(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Build environment prepared.");

                buildExecutionSession.setRunningEnvironment(runningEnvironment);
                buildExecutionSession.setStatus(BuildExecutionStatus.BUILD_ENV_SETUP_COMPLETE_SUCCESS);
                waitToCompleteFuture.complete(null);
            };
            Consumer<Exception> onError = (e) -> {
                operationLogger.error(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Failed to set-up build environment.");

                buildExecutionSession.setStatus(BuildExecutionStatus.BUILD_ENV_SETUP_COMPLETE_WITH_ERROR);
                waitToCompleteFuture.completeExceptionally(new BuildProcessException(e, startedEnvironment));
            };
            buildExecutionSession.setStatus(BuildExecutionStatus.BUILD_ENV_WAITING);

            startedEnvironment.monitorInitialization(onComplete, onError);
        } catch (Throwable e) {
            waitToCompleteFuture.completeExceptionally(new BuildProcessException(e, startedEnvironment));
        }
        return waitToCompleteFuture;
    }

    private CompletableFuture<CompletedBuild> runTheBuild(DefaultBuildExecutionSession buildExecutionSession) {
        CompletableFuture<CompletedBuild> waitToCompleteFuture = new CompletableFuture<>();
        if (buildExecutionSession.isCanceled()) {
            waitToCompleteFuture.complete(null);
            return waitToCompleteFuture;
        }
        BuildExecutionConfiguration buildExecutionConfiguration = buildExecutionSession.getBuildExecutionConfiguration();
        operationLogger.info(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Setting up build ...");

        buildExecutionSession.setStatus(BuildExecutionStatus.BUILD_SETTING_UP);
        RunningEnvironment runningEnvironment = buildExecutionSession.getRunningEnvironment();

        try {
            Consumer<CompletedBuild> onComplete = waitToCompleteFuture::complete;
            Consumer<Throwable> onError = (e) -> {
                waitToCompleteFuture.completeExceptionally(new BuildProcessException(e, runningEnvironment));
            };

            String buildAgentUrl = runningEnvironment.getBuildAgentUrl();
            String liveLogWebSocketUrl = "ws" + StringUtils.addEndingSlash(buildAgentUrl).replaceAll("http(s?):", ":") + "socket/text/ro";
            log.debug("Setting live log websocket url: {}", liveLogWebSocketUrl);
            buildExecutionSession.setLiveLogsUri(Optional.of(new URI(liveLogWebSocketUrl)));
            BuildDriver buildDriver = buildDriverFactory.getBuildDriver();
            RunningBuild runningBuild = buildDriver
                    .startProjectBuild(buildExecutionSession, runningEnvironment, onComplete, onError);

            buildExecutionSession.setCancelHook(() -> runningBuild.cancel());

            buildExecutionSession.setStatus(BuildExecutionStatus.BUILD_WAITING);
        } catch (Throwable e) {
            throw new BuildProcessException(e, runningEnvironment);
        }
        return waitToCompleteFuture;
    }

    private Void retrieveBuildDriverResults(BuildExecutionSession buildExecutionSession, CompletedBuild completedBuild) {
        if (completedBuild == null) {
            String context = buildExecutionSession.getBuildExecutionConfiguration().getBuildContentId();
            Date expires = buildExecutionSession.getBuildExecutionConfiguration().isTempBuild() ? systemConfig.getTemporalBuildExpireDate() : null;
            operationLogger.warn(context, expires, "Unable to retrieve build driver results. Most likely due to cancelled operation.");
            return null;
        }
        try {
            BuildExecutionConfiguration buildExecutionConfiguration = buildExecutionSession.getBuildExecutionConfiguration();
            operationLogger.info(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Collecting results from build driver ...");

            buildExecutionSession.setStatus(BuildExecutionStatus.COLLECTING_RESULTS_FROM_BUILD_DRIVER);
            BuildDriverResult buildResult = completedBuild.getBuildResult();
            BuildStatus buildStatus = buildResult.getBuildStatus();
            buildExecutionSession.setBuildDriverResult(buildResult);
            if (buildStatus.completedSuccessfully()) {
                operationLogger.info(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Build successfully completed.");
                buildExecutionSession.setStatus(BuildExecutionStatus.BUILD_COMPLETED_SUCCESS);
            } else if (buildStatus.equals(BuildStatus.CANCELLED)) {
                operationLogger.info(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Build has been canceled.");
                buildExecutionSession.setStatus(BuildExecutionStatus.CANCELLED);
            } else {
                operationLogger.warn(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Build completed with errors.");
                buildExecutionSession.setStatus(BuildExecutionStatus.BUILD_COMPLETED_WITH_ERROR);
            }
            return null;
        } catch (Throwable e) {
            throw new BuildProcessException(e, completedBuild.getRunningEnvironment());
        }
    }

    private Void retrieveRepositoryManagerResults(DefaultBuildExecutionSession buildExecutionSession) {
        try {
            if (!buildExecutionSession.hasFailed() && !buildExecutionSession.isCanceled()) {
                BuildExecutionConfiguration buildExecutionConfiguration = buildExecutionSession.getBuildExecutionConfiguration();
                operationLogger.info(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Collecting results from repository manager ...");

                buildExecutionSession.setStatus(BuildExecutionStatus.COLLECTING_RESULTS_FROM_REPOSITORY_MANAGER);
                RunningEnvironment runningEnvironment = buildExecutionSession.getRunningEnvironment();
                if (runningEnvironment == null) {
                    return null;
                }
                RepositorySession repositorySession = runningEnvironment.getRepositorySession();
                if (repositorySession == null) {
                    return null;
                }
                RepositoryManagerResult repositoryManagerResult = repositorySession.extractBuildArtifacts();
                buildExecutionSession.setRepositoryManagerResult(repositoryManagerResult);
                if (repositoryManagerResult.getCompletionStatus().isFailed()) {
                    buildExecutionSession.setStatus(BuildExecutionStatus.COLLECTING_RESULTS_FROM_REPOSITORY_MANAGER_COMPLETED_WITH_ERROR);
                } else {
                    buildExecutionSession.setStatus(BuildExecutionStatus.COLLECTING_RESULTS_FROM_REPOSITORY_MANAGER_COMPLETED_SUCCESS);
                }
                //TODO log indy promotion validation failure
                operationLogger.info(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Collected results from repository manager.");
            }
        } catch (Throwable e) {
            throw new BuildProcessException(e, buildExecutionSession.getRunningEnvironment());
        }
        return null;
    }

    private void destroyEnvironment(BuildExecutionSession buildExecutionSession) {
        try {
            RunningEnvironment runningEnvironment = buildExecutionSession.getRunningEnvironment();
            if (runningEnvironment != null) {
                BuildExecutionConfiguration buildExecutionConfiguration = buildExecutionSession.getBuildExecutionConfiguration();
                operationLogger.info(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Destroying build environment.");
                buildExecutionSession.setStatus(BuildExecutionStatus.BUILD_ENV_DESTROYING);
                runningEnvironment.destroyEnvironment();
                operationLogger.info(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Build environment destroyed.");
                buildExecutionSession.setStatus(BuildExecutionStatus.BUILD_ENV_DESTROYED);
            } else {
                String context = buildExecutionSession.getBuildExecutionConfiguration().getBuildContentId();
                Date expires = buildExecutionSession.getBuildExecutionConfiguration().isTempBuild() ? systemConfig.getTemporalBuildExpireDate() : null;
                operationLogger.warn(context, expires, "Unable to destroy environment. Most likely due to cancelled operation.");
            }
        } catch (Throwable e) {
            throw new BuildProcessException(e);
        }
    }

    private Void completeExecution(DefaultBuildExecutionSession buildExecutionSession, Throwable e) {
        BuildExecutionConfiguration buildExecutionConfiguration = buildExecutionSession.getBuildExecutionConfiguration();
        operationLogger.info(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Finalizing build execution.");

        if (e != null) {
            log.debug("Finalizing FAILED execution. Exception: ", e);
        } else {
            log.debug("Finalizing SUCCESS execution.");
        }

        buildExecutionSession.setStatus(BuildExecutionStatus.FINALIZING_EXECUTION);

        if (buildExecutionSession.getStartTime() == null) {
            buildExecutionSession.setException(new ExecutorException("Missing start time."));
        }
        if (e != null) {
            stopRunningEnvironment(e);
        } else {
            try {
                destroyEnvironment(buildExecutionSession);
            } catch (BuildProcessException destroyException) {
                e = destroyException;
            }
        }

        if (e != null) {
            buildExecutionSession.setException(new ExecutorException(e));
        }

        if (buildExecutionSession.getEndTime() != null) {
            buildExecutionSession.setException(new ExecutorException("End time already set."));
        } else {
            buildExecutionSession.setEndTime(new Date());
        }

        String accessToken = buildExecutionSession.getAccessToken();
        log.debug("Closing Maven repository manager [" + buildExecutionSession.getId() + "].");
        try {
            TargetRepository.Type repoType = BuildTypeToRepositoryType.getRepositoryType(
                    buildExecutionSession.getBuildExecutionConfiguration().getBuildType());
            repositoryManagerFactory.getRepositoryManager(repoType).close(accessToken);
        } catch (ExecutorException executionException) {
            buildExecutionSession.setException(executionException);
        }

        //check if any of previous statuses indicated "failed" state
        if (buildExecutionSession.isCanceled()) {
            operationLogger.info(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Build execution completed (canceled).");
            buildExecutionSession.setStatus(BuildExecutionStatus.CANCELLED);
        } else if (buildExecutionSession.hasFailed()) {
            operationLogger.warn(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Build execution completed with errors.");
            buildExecutionSession.setStatus(BuildExecutionStatus.DONE_WITH_ERRORS);
        } else {
            operationLogger.info(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Build execution completed successfully.");
            buildExecutionSession.setStatus(BuildExecutionStatus.DONE);
        }
        log.debug("Removing buildExecutionTask [" + buildExecutionSession.getId() + "] from list of running tasks.");
        runningExecutions.remove(buildExecutionSession.getId());
        operationLogger.info(buildExecutionConfiguration.getBuildContentId(), getLogExpires(buildExecutionConfiguration), "Build execution completed.");
        return null;
    }

    /**
     * Tries to stop running environment if the exception contains information about running environment
     *
     * @param ex Exception in build process (To stop the environment it has to be instance of BuildProcessException)
     */
    private void stopRunningEnvironment(Throwable ex) {
        DestroyableEnvironment destroyableEnvironment = null;
        if(ex instanceof BuildProcessException) {
            BuildProcessException bpEx = (BuildProcessException) ex;
            destroyableEnvironment = bpEx.getDestroyableEnvironment();
        } else if(ex.getCause() instanceof BuildProcessException) {
            BuildProcessException bpEx = (BuildProcessException) ex.getCause();
            destroyableEnvironment = bpEx.getDestroyableEnvironment();
        } else {
            //It shouldn't never happen - Throwable should be caught in all steps of build chain
            //and BuildProcessException should be thrown instead of that
            log.warn("Possible leak of a running environment! Build process ended with exception, "
                    + "but the exception didn't contain information about running environment.", ex);
        }

        try {
            if (destroyableEnvironment != null) {
                destroyableEnvironment.destroyEnvironment();
            }

        } catch (EnvironmentDriverException envE) {
            log.warn("Running environment" + destroyableEnvironment + " couldn't be destroyed!", envE);
        }
    }

    private Date getLogExpires(BuildExecutionConfiguration buildExecutionConfiguration) {
        return buildExecutionConfiguration.isTempBuild() ? systemConfig.getTemporalBuildExpireDate() : null;
    }

    @Override
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
