package org.jboss.pnc.processes.runtimeproducers;

import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-16.
 */
public class KieSessionProducer {

    @Inject
    RuntimeEngine runtimeEngine;

    @Produces
    KieSession getKieSession() {
        return runtimeEngine.getKieSession();
    }

}
