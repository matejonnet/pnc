package org.jboss.pnc.processes.runtimeproducers;

import org.jboss.pnc.processes.handlers.AsyncServiceTaskHandler;
import org.jbpm.services.task.identity.JBossUserGroupCallbackImpl;
import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.manager.RuntimeEnvironmentBuilder;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.manager.RuntimeManagerFactory;
import org.kie.api.task.UserGroupCallback;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.utils.KieHelper;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import javax.transaction.TransactionManager;
import java.util.Properties;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-04.
 */
public class RuntimeManagerProducer {

    @PersistenceUnit
    EntityManagerFactory entityManagerFactory;

    @Inject
    TransactionManager transactionManager;

    @Produces
    @ApplicationScoped
    public RuntimeManager createRuntimeManager() {

//        KieServices ks = KieServices.Factory.get();
//        KieContainer kContainer = ks.getKieClasspathContainer();
//        KieBase kbase = kContainer.getKieBase("kbase");

        KieHelper kieHelper = new KieHelper();
        KieBase kieBase = kieHelper
                .addResource(ResourceFactory.newClassPathResource("org.jboss.pnc/default-build-process.bpmn2"), ResourceType.BPMN2)
//                .addResource(ResourceFactory.newClassPathResource("org.jboss.pnc/default-build.bpmn" ))
                .build();

        RuntimeEnvironmentBuilder builder = RuntimeEnvironmentBuilder.Factory.get()
                .newDefaultBuilder()
                .entityManagerFactory(entityManagerFactory)
                .addEnvironmentEntry(EnvironmentName.TRANSACTION_MANAGER, transactionManager)
                .knowledgeBase(kieBase);

        Properties properties= new Properties();
        properties.setProperty("mary", "project-tag-editor");
        properties.setProperty("john", "project-config-editor");
        UserGroupCallback userGroupCallback = new JBossUserGroupCallbackImpl(properties);
        builder.userGroupCallback(userGroupCallback);

        RuntimeManager runtimeManager = RuntimeManagerFactory.Factory.get()
                .newSingletonRuntimeManager(builder.get(), "org.jboss.pnc:bpm-manager:1.0");

        //runtimeManager.getRuntimeEngine(null).getKieSession().getWorkItemManager().registerWorkItemHandler("Service Task", new ServiceTaskHandler());
        runtimeManager.getRuntimeEngine(null).getKieSession().getWorkItemManager().registerWorkItemHandler("Service Task", new AsyncServiceTaskHandler());

        return runtimeManager;
    }

}
