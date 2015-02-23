package org.jboss.pnc.processes.tasks;

import org.jboss.pnc.model.User;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.TaskSummary;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-20.
 */
public interface UserTask {
    Integer getProductId();

    Integer getTaskId();

    User getUser();

    void setTask(TaskSummary task);

    void setTaskService(TaskService taskService);
}
