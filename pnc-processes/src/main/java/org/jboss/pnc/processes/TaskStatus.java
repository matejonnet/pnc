package org.jboss.pnc.processes;

import org.kie.api.task.model.Status;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-18.
 */
public enum TaskStatus {

    NEW, RUNNING, DONE, FAILED;

    public static TaskStatus fromStatus(Status status) {
        switch (status) {
            case Created: case Ready:
                return NEW;

            case InProgress: case Suspended:
                return RUNNING;

            case Completed:
                return DONE;

            case Failed:case Error: case Exited:
                return FAILED;

            case Reserved: case Obsolete: //TODO

        }
        throw new RuntimeException("Unhandled status " + status);
    }
}
