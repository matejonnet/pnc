package org.jboss.pnc.processes;

import java.util.function.Consumer;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-18.
 */
public class TaskStatusListener {
    private long taskId;
    private Consumer<TaskStatus> onStatusUpdate;

    public TaskStatusListener(long taskId, Consumer<TaskStatus> onStatusUpdate) {
        this.taskId = taskId;
        this.onStatusUpdate = onStatusUpdate;
    }

    public long getTaskId() {
        return taskId;
    }

    void onStatusUpdate(TaskStatus newStatus) {
        onStatusUpdate.accept(newStatus);
    }

}
