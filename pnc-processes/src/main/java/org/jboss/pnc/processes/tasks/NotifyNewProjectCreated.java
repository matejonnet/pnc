package org.jboss.pnc.processes.tasks;

import org.kie.api.runtime.process.ProcessContext;

/**
 * To call this class from process definition script use:
 * org.jboss.pnc.processes.NotifyNewProjectCreated.NEW_INSTANCE(kcontext);
 *
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-06.
 *
 * @Deprecated used only as example
 */
public class NotifyNewProjectCreated {

    private ProcessContext kcontext;

    public NotifyNewProjectCreated(ProcessContext kcontext) {
        this.kcontext = kcontext;
        System.out.println("Process name: " + kcontext.getProcessInstance().getProcessName());
        System.out.println("Process instance id: " + kcontext.getProcessInstance().getId());
    }

    public static NotifyNewProjectCreated NEW_INSTANCE(ProcessContext kcontext) {
        return new NotifyNewProjectCreated(kcontext);
    }

}
