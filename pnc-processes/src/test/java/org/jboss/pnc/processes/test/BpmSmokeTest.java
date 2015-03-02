package org.jboss.pnc.processes.test;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.logging.Logger;
import org.jboss.pnc.model.Product;
import org.jboss.pnc.processes.ProductReleaseCycleManager;
import org.jboss.pnc.processes.tasks.BasicProductConfiguration;
import org.jboss.pnc.processes.test.mock.DatastoreMock;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.task.model.TaskSummary;

import javax.inject.Inject;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import java.io.File;
import java.util.List;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-03-02.
 */
@RunWith(Arquillian.class)
public class BpmSmokeTest {

    private static final Logger log = Logger.getLogger(BpmSmokeTest.class);

    @Deployment
    public static WebArchive createDeployment() {

        WebArchive war = ShrinkWrap.create(WebArchive.class)
                .addPackages(true, ProductReleaseCycleManager.class.getPackage())
                .addPackage(DatastoreMock.class.getPackage())
                .addAsManifestResource(EmptyAsset.INSTANCE, "kproject.xml")
                .addAsResource("META-INF/test-persistence.xml", "META-INF/persistence.xml")
                .addAsResource("META-INF/Taskorm.xml")
                .addAsResource("META-INF/TaskAuditorm.xml")
                .addAsResource("META-INF/logging.properties")
                .addAsWebInfResource("META-INF/beans.xml", "beans.xml")
                .addAsResource("org.jboss.pnc/default-build-process.bpmn2")
                .addAsResource("test-ds.xml", "ds.xml");

        JavaArchive[] libs = Maven.configureResolver()
                .withMavenCentralRepo(true)
                .loadPomFromFile(new File(getProcessesRoot().getAbsolutePath(), "pom.xml"))
                .importRuntimeDependencies()
                .resolve()
                .withoutTransitivity()
                .as(JavaArchive.class);
        war.addAsLibraries(libs);

        /** Default pom cannot be used to resolve transitivity as there is conflicting lib that must be excluded. */
        JavaArchive[] libsDependencies = Maven.configureResolver()
                .loadPomFromFile(new File(getProcessesRoot().getAbsolutePath(), "src/test/resources/dependencies-pom.xml"))
                .importRuntimeDependencies()
                .resolve()
                .withTransitivity()
                .as(JavaArchive.class);
        war.addAsLibraries(libs);
        war.addAsLibraries(libsDependencies);

        System.out.println(war.toString(true));
        return war;
    }

    private static File getProcessesRoot() {
        File cwd = new File(".");
        if (cwd.getName().equals("pnc-processes")) {
            return cwd;
        } else {
            //if we are not in pnc-processes folder then we must be in its parent
            return new File("./pnc-processes");
        }
    }

    @Inject
    ProductReleaseCycleManager productReleaseCycleManager;

    @Test
    @InSequence(10)
    public void startProcessTestCase() throws HeuristicRollbackException, RollbackException, NotSupportedException, HeuristicMixedException, SystemException {
        BasicProductConfiguration basicConfiguration = getInvalidBasicConfiguration();
        Product product = productReleaseCycleManager.startNewProductRelease(basicConfiguration);
        Assert.assertNull(product);

        basicConfiguration = getValidBasicConfiguration();
        product = productReleaseCycleManager.startNewProductRelease(basicConfiguration);
        Assert.assertNotNull(product);

        Integer productId = product.getId();
        Assert.assertTrue("Mock datastore should create product with id = 1.", productId == 1);
    }

    @Test
    @InSequence(20)
    public void getTasksTestCase() throws HeuristicRollbackException, RollbackException, NotSupportedException, HeuristicMixedException, SystemException {
        Integer productId = 1;

        ProcessInstance processInstance = productReleaseCycleManager.getProcessInstance(productId);
        Assert.assertNotNull(processInstance);

        List<TaskSummary> allTasks = productReleaseCycleManager.getAllTasks(processInstance);
        Assert.assertTrue("There should be some tasks.", allTasks.size() > 0);
    }

    private BasicProductConfiguration getInvalidBasicConfiguration() {
        return new BasicProductConfiguration("", "test-product");
    }

    private BasicProductConfiguration getValidBasicConfiguration() {
        return new BasicProductConfiguration("matejonnet", "test-product");
    }

}
