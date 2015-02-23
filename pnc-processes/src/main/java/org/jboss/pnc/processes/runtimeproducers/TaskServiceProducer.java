package org.jboss.pnc.processes.runtimeproducers;

import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.task.TaskService;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-16.
 */
public class TaskServiceProducer {

    @Inject
    RuntimeEngine runtimeEngine;

    @Produces
    TaskService getTaskService() {
        return runtimeEngine.getTaskService();
    }

}
