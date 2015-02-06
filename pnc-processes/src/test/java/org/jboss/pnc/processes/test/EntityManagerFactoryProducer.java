package org.jboss.pnc.processes.test;

import javax.enterprise.inject.Produces;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-04.
 */
public class EntityManagerFactoryProducer {

//    EntityManagerFactory entityManagerFactory;

    public EntityManagerFactoryProducer() {
//        InitialContext ctx = new InitialContext();
//        NamingManager.setInitialContextFactoryBuilder();
//        entityManagerFactory = Persistence.createEntityManagerFactory("org.jbpm.persistence.jpa");
    }


    @Produces
    public EntityManagerFactory produceEntityManagerFactory() {
//        startH2Server();
//        setupDataSource();
        return Persistence.createEntityManagerFactory("org.jbpm.persistence.jpa");
    }

//    @Produces
//    public EntityManager produceEntityManager() {
//        return entityManagerFactory.createEntityManager();
//    }

//    public static void setupInitialcontex() {
//        String password = "s3cret";
//        Map<String, String> env = new HashMap<String, String>();
//        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
//        env.put(Context.PROVIDER_URL, "ldap://localhost:389/dc=userdev,dc=local");
//        env.put(Context.SECURITY_AUTHENTICATION, "simple");
//        //env.put(Context.SECURITY_PRINCIPAL, "uid="+ username +"cn=users"); // replace with user DN
//        env.put(Context.SECURITY_PRINCIPAL, "cn=dcmanager,cn=users,dc=userdev,dc=local"); // replace with user DN
//        env.put(Context.SECURITY_CREDENTIALS, password);
//
//        try {
//            DirContext ctx = new InitialDirContext(new Hashtable<>(env));
//        } catch (NamingException e) {
//            e.printStackTrace();
//        }
//    }
}
