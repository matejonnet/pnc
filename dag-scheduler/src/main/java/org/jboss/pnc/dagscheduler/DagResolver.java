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

import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Takes a tasks with dependencies and notifies listeners when the task has all the dependencies resolved.
 * New tasks (parent or dependency) can be added at any time.
 *
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public interface DagResolver {

    default void submitTask(String id) {
        submitTask(id, Collections.emptySet());
    }

    void submitTask(String id, Set<String> dependencies);

    void resolveTask(String id, ResolutionStatus resolutionStatus);

    int getCount();

    void setOnReadyListener(Consumer<String> onTaskReady);

    void setOnCompleteListener(Consumer<CompletedTask> onTaskCompleted);

    Set<String> getDependencies(String id);

    Set<String> getDependents(String id);
}
