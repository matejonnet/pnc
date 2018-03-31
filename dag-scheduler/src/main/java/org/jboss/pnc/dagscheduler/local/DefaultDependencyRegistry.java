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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
class DefaultDependencyRegistry implements DependencyRegistry {

    private final Map<String, Set<String>> dependencies = new ConcurrentHashMap<>();

    private Set<String> doGetDependencies(String id) {
        return this.dependencies.computeIfAbsent(id, (t) -> new HashSet<>());
    }

    private Set<String> doGetDependents(String id) {
        Set<String> dependents = new HashSet<>();
        for (Map.Entry<String, Set<String>> dependenciesEntry : this.dependencies.entrySet()) {
            for (String dependency : dependenciesEntry.getValue()) {
                if (dependency.equals(id)) {
                    dependents.add(dependenciesEntry.getKey());
                }
            }
        }
        return dependents;
    }

    @Override
    public Set<String> getDependencies(String id) {
        return new HashSet<>(doGetDependencies(id));
    }

    @Override
    public Set<String> getDependents(String id) {
        return new HashSet<>(doGetDependents(id));
    }

    @Override
    public boolean addDependency(String parentId, String childId) {
        doGetDependents(childId).add(parentId);
        return doGetDependencies(parentId).add(childId);
    }

    @Override
    public boolean removeDependency(String parentId, String childId) {
        doGetDependents(childId).remove(parentId);
        return doGetDependencies(parentId).remove(childId);
    }

    @Override
    public boolean hasDependencies(String id) {
        return getDependencies(id).size() > 0;
    }
}
