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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Task<T extends Serializable> implements Serializable {

    private final String id;
    private final Set<Task<T>> dependencies = new CopyOnWriteArraySet<>();
    private final Set<Task<T>> dependents = new CopyOnWriteArraySet<>();

    private final T data;

    public static <T extends Serializable> Task create(String id, T data) {
        return new Task(id, data);
    }

    public static <T extends Serializable> Task create(String id, T data, Task dependency) {
        Task task = new Task(id, data, dependency);
        dependency.addDependent(task);
        return task;
    }

    private Task(String id, T data) {
        this.id = id;
        this.data = data;
    }

    private Task(String id, T data, Task dependency) {
        this.id = id;
        this.data = data;
        dependencies.add(dependency);
    }

    private void addDependent(Task node) {
        dependents.add(node);
    }

    boolean removeDependency(Task task) {
        return dependencies.remove(task);
    }

    Set<Task<T>> getDependencies() {
        return dependencies;
    }

    Set<Task<T>> getDependents() {
        return dependents;
    }

    public String getId() {
        return id;
    }

    public T getData() {
        return data;
    }
}
