package org.jboss.pnc.core.test.bpm;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.pnc.core.bpm.JBPMSession;
import org.jboss.pnc.core.bpm.ProductBuildProcessInstance;
import org.jboss.pnc.core.bpm.ProductProcessManager;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.runtime.process.ProcessInstance;

import javax.inject.Inject;
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
                .addPackage(JBPMSession.class.getPackage())
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsManifestResource(EmptyAsset.INSTANCE, "kproject.xml")
                .addAsResource("META-INF/logging.properties")
                .addAsResource("org.jboss.pnc/default-build.bpmn");

        System.out.println(jar.toString(true));
        return jar;
    }

    @Inject
    ProductProcessManager productProcessManager;

//    @Inject
//    TaskService taskService;

    @Test
    public void listTasksTestCase() {
        ProductBuildProcessInstance productBuildProcessInstance = productProcessManager.startBuild();
        ProcessInstance processInstance = productBuildProcessInstance.getProcessInstance();
        log.info("Process instance id: " + processInstance.getId());
        log.info("Process id: " + processInstance.getProcessId());
        log.info("Process name: " + processInstance.getProcessName());
        log.info("Process state: " + processInstance.getState());

//        List<Long> tasksByProcessInstanceId = taskService.getTasksByProcessInstanceId(processInstance.getId());
//        tasksByProcessInstanceId.forEach((taskId) -> log.info("Task id: " + taskId));
    }
}
