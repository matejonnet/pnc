package org.jboss.pnc.processes;

import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-10.
 */
public class ServiceTaskCompleteHandler {

    private final WorkItemManager manager;
    private final WorkItem workItem;

    public ServiceTaskCompleteHandler(WorkItemManager manager, WorkItem workItem) {
        this.manager = manager;
        this.workItem = workItem;
    }

    public void complete(Object result) {
        Map<String, Object> results = new HashMap<>();
        results.put("Result", result);
        manager.completeWorkItem(workItem.getId(), results);
    }
}
