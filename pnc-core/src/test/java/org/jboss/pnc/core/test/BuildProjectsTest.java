package org.jboss.pnc.core.test;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.pnc.common.Configuration;
import org.jboss.pnc.core.BuildDriverFactory;
import org.jboss.pnc.core.RepositoryManagerFactory;
import org.jboss.pnc.core.builder.BuildCoordinator;
import org.jboss.pnc.core.builder.BuildTask;
import org.jboss.pnc.core.exception.CoreException;
import org.jboss.pnc.core.test.mock.BuildDriverMock;
import org.jboss.pnc.core.test.mock.DatastoreMock;
import org.jboss.pnc.model.BuildCollection;
import org.jboss.pnc.model.ProjectBuildConfiguration;
import org.jboss.pnc.model.ProjectBuildResult;
import org.jboss.pnc.model.builder.EnvironmentBuilder;
import org.jboss.pnc.spi.BuildStatus;
import org.jboss.pnc.spi.environment.EnvironmentDriverProvider;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2014-11-23.
 */
@RunWith(Arquillian.class)
public class BuildProjectsTest {

    @Deployment
    public static JavaArchive createDeployment() {

            JavaArchive jar = ShrinkWrap.create(JavaArchive.class)
                .addClass(Configuration.class)
                .addClass(BuildDriverFactory.class)
                .addClass(RepositoryManagerFactory.class)
                .addClass(EnvironmentBuilder.class)
                .addClass(EnvironmentDriverProvider.class)
                .addPackage(BuildCoordinator.class.getPackage())
                .addPackage(BuildDriverMock.class.getPackage())
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsResource("META-INF/logging.properties")
                ;

        System.out.println(jar.toString(true));
        return jar;
    }

    @Inject
    BuildCoordinator buildCoordinator;

    @Inject
    DatastoreMock datastore;

    private static final Logger log = Logger.getLogger(BuildProjectsTest.class.getName());

    @Test
    @InSequence(10)
    public void dependsOnItselfConfigurationTestCase() throws Exception {
        TestProjectConfigurationBuilder configurationBuilder = new TestProjectConfigurationBuilder();

        ProjectBuildConfiguration projectBuildConfiguration = configurationBuilder.buildConfigurationWhichDependsOnItself();
        BuildTask buildTask = buildCoordinator.build(projectBuildConfiguration, new HashSet<>(), new HashSet<Consumer<String>>());
        Assert.assertEquals(BuildStatus.REJECTED, buildTask.getStatus());
        Assert.assertTrue("Invalid status description: " + buildTask.getStatusDescription(), buildTask.getStatusDescription().contains("itself"));
    }

    @Test
    @InSequence(15)
    public void cycleConfigurationTestCase() throws Exception {
        TestProjectConfigurationBuilder configurationBuilder = new TestProjectConfigurationBuilder();

        ProjectBuildConfiguration projectBuildConfiguration = configurationBuilder.buildConfigurationWithCycleDependency();
        BuildTask buildTask = buildCoordinator.build(projectBuildConfiguration, new HashSet<>(), new HashSet<Consumer<String>>());
        Assert.assertEquals(BuildStatus.REJECTED, buildTask.getStatus());
        Assert.assertTrue("Invalid status description: " + buildTask.getStatusDescription(), buildTask.getStatusDescription().contains("Cycle dependencies found"));
    }

    @Test
    @InSequence(20)
    public void buildSingleProjectTestCase() throws Exception {
        BuildCollection buildCollection = new TestBuildCollectionBuilder().build("foo", "Foo desc.", "1.0");
        TestProjectConfigurationBuilder configurationBuilder = new TestProjectConfigurationBuilder();
        buildProject(configurationBuilder.build(1, "c1-java"), buildCollection);
    }

    @Test
    @InSequence(30)
    public void buildMultipleProjectsTestCase() throws Exception {
        log.info("Start multiple projects build test.");
        long startTime = System.currentTimeMillis();
        BuildCollection buildCollection = new TestBuildCollectionBuilder().build("foo", "Foo desc.", "1.0");
        TestProjectConfigurationBuilder configurationBuilder = new TestProjectConfigurationBuilder();

        Function<TestBuildConfig, Runnable> createJob = (config) -> {
            Runnable task = () -> {
                try {
                    buildProject(config.configuration, config.collection);
                } catch (InterruptedException | CoreException e) {
                    throw new AssertionError("Something went wrong.", e);
                }
            };
            return task;
        };

        List<Runnable> list = new ArrayList();
        for (int i = 0; i < 100; i++) { //create 100 project configurations
            list.add(createJob.apply(new TestBuildConfig(configurationBuilder.build(i, "c" + i + "-java"), buildCollection)));
        }

        Function<Runnable, Thread> runInNewThread = (r) -> {
            Thread t = new Thread(r);
            t.start();
            return t;
        };

        Consumer<Thread> waitToComplete = (t) -> {
            try {
                t.join(30000);
            } catch (InterruptedException e) {
                throw new AssertionError("Interrupted while waiting threads to complete", e);
            }
        };

        List<Thread> threads = list.stream().map(runInNewThread).collect(Collectors.toList());

        Assert.assertTrue("There are no running builds.", buildCoordinator.getBuildTasks().size() > 0);
        BuildTask buildTask = buildCoordinator.getBuildTasks().iterator().next();
        Assert.assertTrue("Build has no status.", buildTask.getStatus() != null);

        threads.forEach(waitToComplete);
        log.info("Completed multiple projects build test in " + (System.currentTimeMillis() - startTime) + "ms.");
    }

    @Test
    @InSequence(40)
    public void checkDatabaseForResult() {
        List<ProjectBuildResult> buildResults = datastore.getBuildResults();
        Assert.assertTrue("Missing datastore results.", buildResults.size() > 10);

        ProjectBuildResult projectBuildResult = buildResults.get(0);
        String buildLog = projectBuildResult.getBuildLog();
        Assert.assertTrue("Invalid build log.", buildLog.contains("Finished: SUCCESS"));
    }

    private void buildProject(ProjectBuildConfiguration projectBuildConfiguration, BuildCollection buildCollection) throws InterruptedException, CoreException {
        log.info("Building project " + projectBuildConfiguration.getIdentifier());
        List<BuildStatus> receivedStatuses = new ArrayList();

        int nStatusUpdates = 7;

        final Semaphore semaphore = new Semaphore(nStatusUpdates);

        Consumer<BuildStatus> onStatusUpdate = (newStatus) -> {
            receivedStatuses.add(newStatus);
            semaphore.release(1);
            log.fine("Received status update " + newStatus.toString() + " for project " + projectBuildConfiguration.getId());
            log.finer("Semaphore released, there are " + semaphore.availablePermits() + " free entries.");
        };
        Set<Consumer<BuildStatus>> statusUpdateListeners = new HashSet<>();
        statusUpdateListeners.add(onStatusUpdate);
        semaphore.acquire(nStatusUpdates);
        BuildTask buildTask = buildCoordinator.build(projectBuildConfiguration, statusUpdateListeners, new HashSet<Consumer<String>>());

        List<BuildStatus> errorStates = Arrays.asList(BuildStatus.REJECTED, BuildStatus.SYSTEM_ERROR);
        if (errorStates.contains(buildTask.getStatus())) {
            throw new AssertionError("Build " + buildTask.getId() + " has status:" + buildTask.getStatus() + " with description: " + buildTask.getStatusDescription() + "");
        }

        log.fine("Build " + buildTask.getId() + " has been submitted.");
        if (!semaphore.tryAcquire(nStatusUpdates, 15, TimeUnit.SECONDS)) { //wait for callback to release
            log.warning("Build " + buildTask.getId() + " has status:" + buildTask.getStatus() + " with description: " + buildTask.getStatusDescription() + ".");
            throw new AssertionError("Timeout while waiting for status updates.");
        }

        assertStatusUpdateReceived(receivedStatuses, BuildStatus.REPO_SETTING_UP);
        assertStatusUpdateReceived(receivedStatuses, BuildStatus.BUILD_SETTING_UP);
        assertStatusUpdateReceived(receivedStatuses, BuildStatus.BUILD_WAITING);
        assertStatusUpdateReceived(receivedStatuses, BuildStatus.BUILD_COMPLETED_SUCCESS);
        assertStatusUpdateReceived(receivedStatuses, BuildStatus.STORING_RESULTS);
    }

    private void assertStatusUpdateReceived(List<BuildStatus> receivedStatuses, BuildStatus status) {
        boolean received = false;
        for (BuildStatus receivedStatus : receivedStatuses) {
            if (receivedStatus.equals(status)) {
                received = true;
                break;
            }
        }
        Assert.assertTrue("Did not received status update for " + status +".", received );
    }

    private class TestBuildConfig {
        private final ProjectBuildConfiguration configuration;
        private final BuildCollection collection;

        TestBuildConfig(ProjectBuildConfiguration configuration, BuildCollection collection) {
            this.configuration = configuration;
            this.collection = collection;
        }
    }
}
