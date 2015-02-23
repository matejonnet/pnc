package org.jboss.pnc.processes.runtimeproducers;

import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-16.
 */
public class RuntimeEngineProducer {

    @Inject
    RuntimeManager runtimeManager;

    @Produces
    public RuntimeEngine getRuntimeEngine() {
        return runtimeManager.getRuntimeEngine(null);
    }

}
