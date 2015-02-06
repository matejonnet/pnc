package org.jboss.pnc.processes;

import org.kie.api.KieBase;
import org.kie.api.runtime.manager.RuntimeEnvironmentBuilder;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.manager.RuntimeManagerFactory;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.utils.KieHelper;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-04.
 */
public class RuntimeManagerProducer {

    @PersistenceUnit
    EntityManagerFactory entityManagerFactory;

    @Produces
    @ApplicationScoped
    public RuntimeManager createRuntimeManager() {

//        KieServices ks = KieServices.Factory.get();
//        KieContainer kContainer = ks.getKieClasspathContainer();
//        KieBase kbase = kContainer.getKieBase("kbase");

        KieHelper kieHelper = new KieHelper();
        KieBase kieBase = kieHelper
                .addResource(ResourceFactory.newClassPathResource("org.jboss.pnc/default-build.bpmn"))
                .build();

        RuntimeEnvironmentBuilder builder = RuntimeEnvironmentBuilder.Factory.get()
                .newDefaultBuilder().entityManagerFactory(entityManagerFactory)
                .knowledgeBase(kieBase);

        return RuntimeManagerFactory.Factory.get()
                .newSingletonRuntimeManager(builder.get(), "org.jboss.pnc:bpm-manager:1.0");
    }
}
