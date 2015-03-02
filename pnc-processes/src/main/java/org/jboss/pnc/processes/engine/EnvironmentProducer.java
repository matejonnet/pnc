package org.jboss.pnc.processes.engine;

import org.drools.core.impl.EnvironmentFactory;
import org.jbpm.process.audit.event.AuditEventBuilder;
import org.jbpm.runtime.manager.impl.cdi.InjectableRegisterableItemsFactory;
import org.jbpm.services.task.identity.JBossUserGroupCallbackImpl;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.manager.RuntimeEnvironment;
import org.kie.api.runtime.manager.RuntimeEnvironmentBuilder;
import org.kie.api.task.UserGroupCallback;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.runtime.manager.cdi.qualifier.PerProcessInstance;
import org.kie.internal.runtime.manager.cdi.qualifier.PerRequest;
import org.kie.internal.runtime.manager.cdi.qualifier.Singleton;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import java.util.Properties;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-24.
 */
@ApplicationScoped
public class EnvironmentProducer {

    @PersistenceUnit(unitName = "org.jbpm.domain")
    private EntityManagerFactory emf;

    @Inject
    private BeanManager beanManager;

    @Produces
    public EntityManagerFactory getEntityManagerFactory() {
        return this.emf;
    }

    @Produces
    @RequestScoped
    public EntityManager getEntityManager() {
        EntityManager em = emf.createEntityManager();
        return em;
    }

    public void close(@Disposes EntityManager em) {
        em.close();
    }


    @Produces
    public UserGroupCallback produceUserGroupCallback() {
        //TODO usergroup callback
        Properties properties= new Properties();
        properties.setProperty("mary", "project-tag-editor");
        properties.setProperty("john", "project-config-editor");
        UserGroupCallback userGroupCallback = new JBossUserGroupCallbackImpl(properties);
        return userGroupCallback;
    }

    @Produces
    @Singleton
    @PerRequest
    @PerProcessInstance
    public RuntimeEnvironment produceEnvironment(EntityManagerFactory emf) {
        Environment env = EnvironmentFactory.newEnvironment();
        RuntimeEnvironment runtimeEnvironment = RuntimeEnvironmentBuilder.Factory.get()
                .newDefaultBuilder()
                .entityManagerFactory(emf)
                .userGroupCallback(produceUserGroupCallback())
                .registerableItemsFactory(InjectableRegisterableItemsFactory.getFactory(beanManager, (AuditEventBuilder) null))
                .addAsset(ResourceFactory.newClassPathResource("org.jboss.pnc/default-build-process.bpmn2"), ResourceType.BPMN2)
                .get();
        runtimeEnvironment.usePersistence();
        return runtimeEnvironment;
    }

}
