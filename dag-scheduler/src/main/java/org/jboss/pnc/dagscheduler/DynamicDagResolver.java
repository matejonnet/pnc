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

import java.io.Serializable;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public interface DynamicDagResolver<T extends Serializable> {

    void submitTask(Task<T> task);

    void resolveTask(String id, CompletedTask.Status status);

    void resolveTask(Task task, CompletedTask.Status status);

    int getCount();

    void setOnReadyListener(Consumer<Task<T>> onTaskReady);

    void setOnCompleteListener(Consumer<CompletedTask> onTaskCompleted);

    void submitTasks(Collection<Task<T>> tasks);

    //    void setOnTaskReady(Consumer<Task<T>> task);
//
//    void setOnTaskCompleted(Consumer<CompletedTask<T>> task);

}
