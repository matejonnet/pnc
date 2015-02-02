package org.jboss.pnc.core.bpm;

import org.kie.api.runtime.process.ProcessInstance;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-01-16.
 */
public class ProductBuildProcessInstance {
    private ProcessInstance processInstance;

    public ProductBuildProcessInstance(ProcessInstance processInstance) {
        this.processInstance = processInstance;
    }

    public ProcessInstance getProcessInstance() {
        return processInstance;
    }
}
