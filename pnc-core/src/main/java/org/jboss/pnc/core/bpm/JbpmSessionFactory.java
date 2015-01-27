package org.jboss.pnc.core.bpm;

import org.kie.api.task.TaskService;
import org.kie.internal.utils.KieHelper;

import javax.inject.Inject;
import java.io.IOException;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-01-09.
 */
public class JbpmSessionFactory {


    @Inject
    TaskService taskService;

    public void jbpmSession() throws IOException {

        KieHelper kieHelper = new KieHelper();
                //.build();

/*
        KieSession kieSession = kieBase.newKieSession();

        ProcessInstance processInstance = kieSession.startProcess("com.sample.MyProcess");

        RuntimeEnvironmentBuilder runtimeEnvironmentBuilder = RuntimeEnvironmentBuilder.Factory.get().newDefaultBuilder();

        taskService.getTasksAssignedAsPotentialOwner()
*/
    }
}
