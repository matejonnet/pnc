package org.jboss.pnc.processes.tasks;

import org.jboss.pnc.processes.handlers.ServiceTaskCompleteHandler;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-06.
 */
public class BuildProjectAsync {

    private ServiceTaskCompleteHandler serviceTaskCompleteHandler;

    public BuildProjectAsync(ServiceTaskCompleteHandler serviceTaskCompleteHandler) {
        this.serviceTaskCompleteHandler = serviceTaskCompleteHandler;
    }

    public void run(String s) {
        System.out.println("Running async service ...");
        serviceTaskCompleteHandler.complete("Async result.");
    }
}
