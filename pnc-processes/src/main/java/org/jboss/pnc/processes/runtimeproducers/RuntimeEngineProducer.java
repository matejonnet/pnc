package org.jboss.pnc.processes.runtimeproducers;

import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.internal.runtime.manager.cdi.qualifier.Singleton;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-16.
 */
public class RuntimeEngineProducer {

    @Inject
    @Singleton
    RuntimeManager runtimeManager;

    @Produces
    public RuntimeEngine getRuntimeEngine() {
        return runtimeManager.getRuntimeEngine(null);
    }

}
