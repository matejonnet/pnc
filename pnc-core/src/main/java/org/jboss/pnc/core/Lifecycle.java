package org.jboss.pnc.core;

import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;


/**
 *
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2014-12-16.
 */
@ApplicationScoped
public class Lifecycle {

    public static final Logger log = Logger.getLogger(Lifecycle.class);

    //EntityManagerFactory emf;

    public Lifecycle() {
//        emf = Persistence.createEntityManagerFactory("org.jbpm.persistence.jpa");
    }

    public void start() {
        log.info("Core started.");
    }

    public void stop() {
        log.info("Core stopped.");
    }

//    @Produces
//    EntityManager getEntityManager() {
//        return emf.createEntityManager();
//    }

}
