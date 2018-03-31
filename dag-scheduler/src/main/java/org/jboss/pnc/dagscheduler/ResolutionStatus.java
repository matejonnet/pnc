/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.dagscheduler;

/**
 * Status used to notify resolved that a task execution is completed.
 *
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public enum ResolutionStatus {

    SUCCESS(true, Status.SUCCESS),
    FAILED(false, Status.FAILED),
    CANCELLED(false, Status.CANCELED),
    TIME_OUT(false, Status.TIME_OUT),
    SYSTEM_ERROR(false, Status.SYSTEM_ERROR);

    private boolean success;

    private Status completedStatus;

    ResolutionStatus(boolean success, Status completedStatus) {
        this.success = success;
        this.completedStatus = completedStatus;
    }

    public boolean isSuccess() {
        return success;
    }

    public Status toCompletionStatus() {
        return completedStatus;
    }
}
