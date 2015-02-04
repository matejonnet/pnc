package org.jboss.pnc.processes.test;

import bitronix.tm.resource.jdbc.PoolingDataSource;
import org.h2.tools.Server;

import javax.enterprise.inject.Produces;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.Properties;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-04.
 */
public class EntityManagerFactoryProducer {

    @Produces
    public EntityManagerFactory produceEntityManagerFactory() {
        startH2Server();
        setupDataSource();
        return Persistence.createEntityManagerFactory("org.jbpm.persistence.jpa");
    }

    public static Server startH2Server() {
        try {
            // start h2 in memory database
            Server server = Server.createTcpServer(new String[0]);
            server.start();
            return server;
        } catch (Throwable t) {
            throw new RuntimeException("Could not start H2 server", t);
        }
    }

    public static PoolingDataSource setupDataSource() {
        Properties properties = getProperties();
        // create data source
        PoolingDataSource pds = new PoolingDataSource();
        pds.setUniqueName(properties.getProperty("persistence.datasource.name", "jdbc/jbpm-ds"));
        pds.setClassName("bitronix.tm.resource.jdbc.lrc.LrcXADataSource");
        pds.setMaxPoolSize(5);
        pds.setAllowLocalTransactions(true);
        pds.getDriverProperties().put("user", properties.getProperty("persistence.datasource.user", "sa"));
        pds.getDriverProperties().put("password", properties.getProperty("persistence.datasource.password", ""));
        pds.getDriverProperties().put("url", properties.getProperty("persistence.datasource.url", "jdbc:h2:tcp://localhost/~/jbpm-db;MVCC=TRUE"));
        pds.getDriverProperties().put("driverClassName", properties.getProperty("persistence.datasource.driverClassName", "org.h2.Driver"));
        pds.init();
        return pds;
    }

    public static Properties getProperties() {
        Properties properties = new Properties();
        try {
            properties.load(EntityManagerFactoryProducer.class.getResourceAsStream("/jBPM.properties"));
        } catch (Throwable t) {
            new RuntimeException("Cannot load jBPM.properties.", t);
        }
        return properties;
    }
}
