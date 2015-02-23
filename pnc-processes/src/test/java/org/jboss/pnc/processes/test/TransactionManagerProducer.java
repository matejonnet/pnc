package org.jboss.pnc.processes.test;

import bitronix.tm.TransactionManagerServices;

import javax.enterprise.inject.Produces;
import javax.transaction.TransactionManager;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-23.
 */
public class TransactionManagerProducer {

    @Produces
    public TransactionManager createTransactionManager() {
        return TransactionManagerServices.getTransactionManager();
    }

}
