package org.jboss.pnc.core.bpm;

import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-01-16.
 */
public class ProductProcessManager {

    @Inject
    KieSession kieSession;

//    @Inject
//    RuntimeManager runtimeManager;

    public ProductBuildProcessInstance startBuild() {
        Map<String, Object> params = new HashMap<>();
        ProcessInstance processInstance = kieSession.startProcess("org.jboss.pnc.DefaultBuild", params);
        return new ProductBuildProcessInstance(processInstance);
    }





}
