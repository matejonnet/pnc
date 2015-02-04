package org.jboss.pnc.processes.test;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.pnc.processes.RuntimeManagerProducer;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.TaskSummary;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-01-29.
 */
@RunWith(Arquillian.class)
public class BpmTest {

    private static final Logger log = Logger.getLogger(BpmTest.class.getName());

    @Deployment
    public static JavaArchive createDeployment() {

        JavaArchive jar = ShrinkWrap.create(JavaArchive.class)
                .addClass(EntityManagerFactoryProducer.class)
                .addPackage(RuntimeManagerProducer.class.getPackage())
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(EmptyAsset.INSTANCE, "kproject.xml")
                .addAsResource("META-INF/logging.properties")
                .addAsResource("META-INF/persistence.xml")
                .addAsResource("org.jboss.pnc/default-build.bpmn")
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
        list = taskService.getTasksAssignedAsPotentialOwner("mary", "en-UK");
        task = list.get(0);
        System.out.println("Mary is executing task " + task.getName());
        taskService.start(task.getId(), "mary");
        taskService.complete(task.getId(), "mary", null);

        runtimeManager.disposeRuntimeEngine(engine);
        System.exit(0);
    }

}
