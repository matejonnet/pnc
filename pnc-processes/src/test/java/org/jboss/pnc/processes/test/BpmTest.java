package org.jboss.pnc.processes.test;

import bitronix.tm.resource.jdbc.PoolingDataSource;
import org.h2.tools.Server;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.pnc.processes.RuntimeManagerProducer;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.TaskSummary;

import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-01-29.
 */
@RunWith(Arquillian.class)
public class BpmTest {

    private static final Logger log = Logger.getLogger(BpmTest.class.getName());

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
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(EmptyAsset.INSTANCE, "kproject.xml")
                //.addAsManifestResource("META-INF/test-persistence.xml", "persistence.xml")
                .addAsManifestResource("META-INF/persistence.xml")
                .addAsManifestResource("META-INF/Taskorm.xml")
                .addAsManifestResource("META-INF/TaskAuditorm.xml")
                .addAsResource("META-INF/logging.properties")
                .addAsResource("org.jboss.pnc/default-build.bpmn")
                .addAsResource("jndi.properties")
                .addAsResource("jBPM.properties");

        System.out.println(jar.toString(true));
        return jar;
    }

//    @Inject
//    ProductProcessManager productProcessManager;

//    @Inject
//    TaskService taskService;

//    @Test
//    public void listTasksTestCase() {
//        ProductBuildProcessInstance productBuildProcessInstance = productProcessManager.startBuild();
//        ProcessInstance processInstance = productBuildProcessInstance.getProcessInstance();
//        log.info("Process instance id: " + processInstance.getId());
//        log.info("Process id: " + processInstance.getProcessId());
//        log.info("Process name: " + processInstance.getProcessName());
//        log.info("Process state: " + processInstance.getState());
//
////        List<Long> tasksByProcessInstanceId = taskService.getTasksByProcessInstanceId(processInstance.getId());
////        tasksByProcessInstanceId.forEach((taskId) -> log.info("Task id: " + taskId));
//    }

    @Inject
    EntityManagerFactory entityManagerFactory;

    @Inject
    RuntimeManager runtimeManager;

    @Test
    public void sampleProcessTestCase() {

        RuntimeEngine engine = runtimeManager.getRuntimeEngine(null);
        KieSession kieSession = engine.getKieSession();
        TaskService taskService = engine.getTaskService();

        Map<String, Object> params = new HashMap<>();
        ProcessInstance processInstance = kieSession.startProcess("org.jboss.pnc.DefaultBuild", params);

        // let john execute Task 1
        List<TaskSummary> list = taskService.getTasksAssignedAsPotentialOwner("john", "en-UK");
        TaskSummary task = list.get(0);
        System.out.println("John is executing task " + task.getName());
        taskService.start(task.getId(), "john");
        taskService.complete(task.getId(), "john", null);

        // let mary execute Task 2
//        list = taskService.getTasksAssignedAsPotentialOwner("mary", "en-UK");
//        task = list.get(0);
//        System.out.println("Mary is executing task " + task.getName());
//        taskService.start(task.getId(), "mary");
//        taskService.complete(task.getId(), "mary", null);

        runtimeManager.disposeRuntimeEngine(engine);
    }

    public static Server startH2Server() {
        try {
            // start h2 in memory database
            Server server = Server.createTcpServer(new String[0]);
            server.start();
            return server;
        } catch (Throwable t) {
            throw new RuntimeException("Could not start H2 server", t);
        }
    }

    public static PoolingDataSource setupDataSource() {
        Properties properties = getProperties();
        // create data source
        PoolingDataSource pds = new PoolingDataSource();
        pds.setUniqueName(properties.getProperty("persistence.datasource.name", "jdbc/jbpm-ds"));
//        pds.setUniqueName(properties.getProperty("persistence.datasource.name", "java:/jbpm-ds"));
        pds.setClassName("bitronix.tm.resource.jdbc.lrc.LrcXADataSource");
        pds.setMaxPoolSize(5);
        pds.setAllowLocalTransactions(true);
        pds.getDriverProperties().put("user", properties.getProperty("persistence.datasource.user", "sa"));
        pds.getDriverProperties().put("password", properties.getProperty("persistence.datasource.password", ""));
        pds.getDriverProperties().put("url", properties.getProperty("persistence.datasource.url", "jdbc:h2:tcp://localhost/~/jbpm-db;MVCC=TRUE"));
        pds.getDriverProperties().put("driverClassName", properties.getProperty("persistence.datasource.driverClassName", "org.h2.Driver"));
        pds.init();
        return pds;
    }

    public static Properties getProperties() {
        Properties properties = new Properties();
        try {
            properties.load(EntityManagerFactoryProducer.class.getResourceAsStream("/jBPM.properties"));
        } catch (Throwable t) {
            new RuntimeException("Cannot load jBPM.properties.", t);
        }
        return properties;
    }


}
