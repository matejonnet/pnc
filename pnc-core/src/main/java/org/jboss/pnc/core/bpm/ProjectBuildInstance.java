package org.jboss.pnc.core.bpm;

import org.kie.api.runtime.process.ProcessInstance;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-01-16.
 */
public class ProjectBuildInstance {
    private ProcessInstance processInstance;

    public ProjectBuildInstance(ProcessInstance processInstance) {
        this.processInstance = processInstance;
    }
}
