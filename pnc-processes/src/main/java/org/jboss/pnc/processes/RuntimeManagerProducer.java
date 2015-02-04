package org.jboss.pnc.processes;

import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.manager.RuntimeEnvironmentBuilder;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.manager.RuntimeManagerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-04.
 */
public class RuntimeManagerProducer {

    @Inject
    EntityManagerFactory entityManagerFactory;

    @Produces
    @ApplicationScoped
    public RuntimeManager createRuntimeManager() {

        KieServices ks = KieServices.Factory.get();
        KieContainer kContainer = ks.getKieClasspathContainer();
        KieBase kbase = kContainer.getKieBase("kbase");

        RuntimeEnvironmentBuilder builder = RuntimeEnvironmentBuilder.Factory.get()
                .newDefaultBuilder().entityManagerFactory(entityManagerFactory)
                .knowledgeBase(kbase);
        return RuntimeManagerFactory.Factory.get()
                .newSingletonRuntimeManager(builder.get(), "org.jboss.pnc:bpm-manager:1.0");
    }
}
