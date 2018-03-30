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
package org.jboss.pnc.dagscheduler.local;

import org.jboss.pnc.dagscheduler.DependencyRegistry;
import org.jboss.pnc.dagscheduler.Task;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
class DefaultDependencyRegistry<T extends Serializable> implements DependencyRegistry<T> {

    private final Map<Task<T>, Set<Task<T>>> dependencies = new ConcurrentHashMap<>();

    private Set<Task<T>> doGetDependencies(Task<T> task) {
        return this.dependencies.computeIfAbsent(task, (t) -> new HashSet<>());
    }

    private Set<Task<T>> doGetDependents(Task<T> task) {
        Set<Task<T>> dependents = new HashSet<>();
        for (Map.Entry<Task<T>, Set<Task<T>>> dependenciesEntry : this.dependencies.entrySet()) {
            for (Task<T> dependency : dependenciesEntry.getValue()) {
                if (dependency.equals(task)) {
                    dependents.add(dependenciesEntry.getKey());
                }
            }
        }
        return dependents;
    }

    @Override
    public Set<Task<T>> getDependencies(Task<T> task) {
        return new HashSet<>(doGetDependencies(task));
    }

    @Override
    public Set<Task<T>> getDependents(Task<T> task) {
        return new HashSet<>(doGetDependents(task));
    }

    @Override
    public boolean addDependency(Task<T> parent, Task<T> child) {
        doGetDependents(child).add(parent);
        return doGetDependencies(parent).add(child);
    }

    @Override
    public boolean removeDependency(Task<T> parent, Task<T> child) {
        doGetDependents(child).remove(parent);
        return doGetDependencies(parent).remove(child);
    }

    @Override
    public boolean hasDependencies(Task<T> task) {
        return getDependencies(task).size() > 0;
    }
}
