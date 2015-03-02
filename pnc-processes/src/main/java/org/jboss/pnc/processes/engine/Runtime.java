package org.jboss.pnc.processes.engine;

import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.task.TaskService;
import org.kie.internal.runtime.manager.cdi.qualifier.Singleton;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-16.
 */
@ApplicationScoped
public class Runtime {

    @Inject
    @Singleton
    RuntimeManager runtimeManager;

    public RuntimeEngine getRuntimeEngine() {
        return runtimeManager.getRuntimeEngine(null);
    }

    public TaskService getTaskService() {
        return getRuntimeEngine().getTaskService();
    }

    public KieSession getKieSession() {
        return getRuntimeEngine().getKieSession();
    }
}
