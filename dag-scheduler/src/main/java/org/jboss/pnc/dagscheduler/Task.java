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

import lombok.Getter;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Task<T extends Serializable> implements Serializable {

    @Getter
    private final String id;

    private final Set<Task<T>> dependencies = new HashSet<>();

    private final Set<Task<T>> dependents = new HashSet<>();

    @Getter
    private final T data;

    private Task(String id, T data) {
        this.id = id;
        this.data = data;
    }

    public static <T extends Serializable> Task create(String id, T data) {
        return create(id, data, Collections.EMPTY_SET);
    }

    public static <T extends Serializable> Task create(String id, T data, Set<Task<T>> dependencies) {
        Task<T> task = new Task(id, data);
        for (Task<T> dependency : dependencies) {
            task.addDependency(dependency);
        }
        return task;
    }

    boolean addDependent(Task node) {
        return dependents.add(node);
    }

    public boolean addDependency(Task task) {
        task.addDependent(this);
        return dependencies.add(task);
    }

    public boolean removeDependency(Task task) {
        return dependencies.remove(task);
    }

    public Set<Task<T>> getDependencies() { //TODO handle concurrent modification exception
        return Collections.unmodifiableSet(dependencies);
    }

    public Set<Task<T>> getDependents() {
        return Collections.unmodifiableSet(dependents);
    }

}
