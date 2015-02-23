package org.jboss.pnc.processes.test;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.pnc.model.Product;
import org.jboss.pnc.model.User;
import org.jboss.pnc.processes.ProductReleaseCycleManager;
import org.jboss.pnc.processes.TaskStatus;
import org.jboss.pnc.processes.TaskStatusListener;
import org.jboss.pnc.processes.handlers.ServiceTaskCompleteHandler;
import org.jboss.pnc.processes.runtimeproducers.RuntimeManagerProducer;
import org.jboss.pnc.processes.tasks.BasicProductConfiguration;
import org.jboss.pnc.processes.tasks.ConfigureRepository;
import org.jboss.pnc.processes.tasks.RepositoryConfiguration;
import org.jboss.pnc.processes.tasks.UserTask;
import org.jboss.pnc.processes.test.mock.DatastoreMock;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.runtime.manager.RuntimeManager;

import javax.inject.Inject;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import java.util.function.Consumer;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-17.
 */
@RunWith(Arquillian.class)
public class ProductReleaseCycleManagerTest extends BpmTestBase {

    private static final Logger log = Logger.getLogger(ProductReleaseCycleManagerTest.class);

    @BeforeClass
    public static void setUp() {
        startH2Server();
        setupDataSource();
    }

    @Deployment
    public static JavaArchive createDeployment() {

        JavaArchive jar = ShrinkWrap.create(JavaArchive.class)
                .addPackage(RuntimeManagerProducer.class.getPackage())
                .addPackage(EntityManagerFactoryProducer.class.getPackage())
                .addPackage(UserTask.class.getPackage())
                .addPackage(ServiceTaskCompleteHandler.class.getPackage())
                .addPackage(ProductReleaseCycleManager.class.getPackage())
                .addPackage(DatastoreMock.class.getPackage())
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(EmptyAsset.INSTANCE, "kproject.xml")
                .addAsResource("META-INF/persistence.xml")
                .addAsResource("META-INF/Taskorm.xml")
                .addAsResource("META-INF/TaskAuditorm.xml")
                .addAsResource("META-INF/logging.properties")
//                .addAsResource("org.jboss.pnc/default-build-process.bpmn2")
                .addAsResource("jndi.properties")
                .addAsResource("jBPM.properties");

        System.out.println(jar.toString(true));
        return jar;
    }

    @Inject
    RuntimeManager runtimeManager;

    @Inject
    ProductReleaseCycleManager productReleaseCycleManager;

    @Test
    public void productReleaseCycleTestCase() throws HeuristicRollbackException, RollbackException, NotSupportedException, HeuristicMixedException, SystemException {
        BasicProductConfiguration basicConfiguration = getInvalidBasicConfiguration();
        Product product = productReleaseCycleManager.startNewProductRelease(basicConfiguration);
        Assert.assertNull(product);

        basicConfiguration = getValidBasicConfiguration();
        product = productReleaseCycleManager.startNewProductRelease(basicConfiguration);
        Assert.assertNotNull(product);

        Integer productId = product.getId();

        Consumer onTask1StatusUpdate = (newTaskStatus) -> {
            log.info("Received task 1 status update:" + newTaskStatus );
        };
        TaskStatusListener task1StatusListener = new TaskStatusListener(1, onTask1StatusUpdate);

        Consumer onTask2StatusUpdate = (newTaskStatus) -> {
            log.info("Received task 2 status update:" + newTaskStatus );
        };
        TaskStatusListener task2StatusListener = new TaskStatusListener(2, onTask2StatusUpdate);

        Consumer onTask3StatusUpdate = (newTaskStatus) -> {
            log.info("Received task 3 status update:" + newTaskStatus );
        };
        TaskStatusListener task3StatusListener = new TaskStatusListener(3, onTask3StatusUpdate);

        TaskStatus task1Status = productReleaseCycleManager.getTaskStatus(productId, 1);
        productReleaseCycleManager.registerTaskStatusUpdateListener(task1StatusListener);

        productReleaseCycleManager.getTaskStatus(productId, 2);
        productReleaseCycleManager.registerTaskStatusUpdateListener(task2StatusListener);

        productReleaseCycleManager.getTaskStatus(productId, 3);
        productReleaseCycleManager.registerTaskStatusUpdateListener(task3StatusListener);


        //once T1 completed
//        productReleaseCycleManager.startUserTask(productId, 2, new User());
//        RepositoryConfiguration repositoryConfiguration = new RepositoryConfiguration();
//        productReleaseCycleManager.completeUserTask(productId, 2, repositoryConfiguration, new User());

        User user = new User();
        user.setUsername("john");
        ConfigureRepository configureRepository = new ConfigureRepository(productId, 2, user);
        productReleaseCycleManager.startUserTask(configureRepository);
        RepositoryConfiguration repositoryConfiguration = new RepositoryConfiguration("http://path.to/product/repo.git");
        configureRepository.completeTask(repositoryConfiguration);

//
//        Assert.assertTrue(receivedTask1Completed);
//
//        //UI is updated with link to fill the form UT2 (user task 2)
//        productReleaseCycleManager.takeTask(2, mlazar);
//
//        //submit invalid data
//        productReleaseCycleManager.sumitTaskData(2, mlazar, form2Data);
//
//        Assert.assertFalse(receivedTask2Completed);
//
//        //submit valid data
//        productReleaseCycleManager.sumitTaskData(2, mlazar, form2DataUpdated);
//
//        Assert.assertTrue(receivedTask2Completed);
//
//        Assert.assertTrue(receivedTask3Completed);


        productReleaseCycleManager.unregisterTaskStatusUpdateListener(task1StatusListener);
        productReleaseCycleManager.unregisterTaskStatusUpdateListener(task2StatusListener);
        productReleaseCycleManager.unregisterTaskStatusUpdateListener(task3StatusListener);

        /*
        * *process definition must be manually synched with UI*
        *
        * - each human task needs a UI support (form)
        * - form can have two titles "enter"|"update" /task name/ data,
        *   where "update" is used in case of returning after validation
        *
        * -
        *
        * * Product Build Phases *
        * - phases are defined by process
        * - list all stages as semaphore with possible parallel branches: completed|running|new
        * - when user task ready: show link to the form
        * - when user form is submitted validate it and continue if basic validation passes,
        *   complex validations should be part of process itself
        *
        * **
        * - connect processId with datastore configuration
        *   - store configurationId to process
        *
        * **
        *
        * */



    }

    private BasicProductConfiguration getInvalidBasicConfiguration() {
        return new BasicProductConfiguration("", "test-product");
    }

    private BasicProductConfiguration getValidBasicConfiguration() {
        return new BasicProductConfiguration("matejonnet", "test-product");
    }

}
