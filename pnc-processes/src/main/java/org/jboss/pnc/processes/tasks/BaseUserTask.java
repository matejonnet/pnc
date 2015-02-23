package org.jboss.pnc.processes.tasks;

import org.jboss.pnc.model.User;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.TaskSummary;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-20.
 */
abstract class BaseUserTask {

    protected Integer taskId;
    protected Integer productId;
    protected TaskSummary task;
    protected User user;
    protected TaskService taskService;

    protected BaseUserTask(Integer taskId, Integer productId, User user) {
        this.taskId = taskId;
        this.productId = productId;
        this.user = user;
    }

    public Integer getProductId() {
        return productId;
    }

    public Integer getTaskId() {
        return taskId;
    }

    public User getUser() {
        return user;
    }

    public void setTask(TaskSummary task) {
        this.task = task;
    }

    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }
}
