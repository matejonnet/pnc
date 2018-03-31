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

import org.jboss.pnc.dagscheduler.local.DefaultDependencyRegistry;
import org.jboss.pnc.dagscheduler.local.DefaultTaskRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class DefaultDagResolver implements DagResolver {

    private final Logger logger = LoggerFactory.getLogger(DefaultDagResolver.class);

    private final TaskRegistry taskRegistry;

    private final DependencyRegistry dependencyRegistry;

    private Consumer<StatusUpdate> onStatusUpdate = (t) -> {};

    public DefaultDagResolver() {
        taskRegistry = new DefaultTaskRegistry();
        dependencyRegistry = new DefaultDependencyRegistry();
    }

    public DefaultDagResolver(TaskRegistry taskRegistry, DependencyRegistry dependencyRegistry) {
        this.taskRegistry = taskRegistry;
        this.dependencyRegistry = dependencyRegistry;
    }

    @Override
    public void setStatusUpdateListener(Consumer<StatusUpdate> onStatusUpdate) {
        this.onStatusUpdate = onStatusUpdate;
    }

    @Override
    public Optional<StatusUpdate> submitTask(String id, Set<String> dependencies) {
        logger.debug("Adding new task {} ...", id);

        if (introducesCycle(id, dependencies)) {
            onStatusUpdate.accept(new StatusUpdate(id, Status.INTRODUCES_CYCLE_DEPENDENCY));
            return Optional.of(new StatusUpdate(id, Status.INTRODUCES_CYCLE_DEPENDENCY));
        }

        for (String dependency : dependencies) {
            dependencyRegistry.addDependency(id, dependency);
        }

        if (!taskRegistry.contains(id)) {
            taskRegistry.add(id);
            onStatusUpdate.accept(new StatusUpdate(id, Status.ENQUEUED));
            wireDependencies(id);
            if (!dependencyRegistry.hasDependencies(id)) { //all dependencies completed
                onStatusUpdate.accept(new StatusUpdate(id, Status.READY));
            } else {
                onStatusUpdate.accept(new StatusUpdate(id, Status.WAITING_DEPENDENCIES));
            }
            return Optional.empty();
        } else {
            onStatusUpdate.accept(new StatusUpdate(id, Status.ALREADY_SUBMITTED));
            return Optional.of(new StatusUpdate(id, Status.ALREADY_SUBMITTED));
        }
    }

    private boolean introducesCycle(String id, Set<String> dependencies) {
        Set<String> allTaskDependents = getDependents(id);
        // check if any dependent depends on any dependency
        for (String dependency : dependencies) {
            for (String transitiveDependency : dependencyRegistry.getAllTaskDependencies(dependency)) {
                if (allTaskDependents.contains(transitiveDependency)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void wireDependencies(String id) {
        for (String dependencyId : dependencyRegistry.getDependencies(id)) {
            if (!taskRegistry.contains(dependencyId)) {
                onStatusUpdate.accept(new StatusUpdate(id, Status.MISSING_DEPENDENCY)); //TODO is it allowed to submit the dependency latter ?
            } else {
                dependencyRegistry.addDependency(id, dependencyId);
            }
        }
    }

    @Override
    public void resolveTask(String id, ResolutionStatus resolutionStatus) {
        logger.debug("Resolving task {} with status {}.", id, resolutionStatus);
        if (resolutionStatus.isSuccess()) {
            success(id);
        } else {
            fail(id, resolutionStatus);
        }
    }

    @Override
    public int getCount() {
        return taskRegistry.size();
    }

    @Override
    public Set<String> getDependencies(String id) {
        return dependencyRegistry.getDependencies(id);
    }

    @Override
    public Set<String> getDependents(String id) {
        return dependencyRegistry.getDependents(id);
    }

    private void fail(String id, ResolutionStatus resolutionStatus) {
        taskRegistry.remove(id);
        onStatusUpdate.accept(new StatusUpdate(id, resolutionStatus.toCompletionStatus()));

        for (String dependentId : dependencyRegistry.getAllTaskDependents(id)) {
            if (taskRegistry.contains(dependentId)) {
                taskRegistry.remove(dependentId);
                onStatusUpdate.accept(new StatusUpdate(dependentId, Status.FAILED_DEPENDENCY));
            }
        }
    }

    private void success(String id) {
        taskRegistry.remove(id);
        onStatusUpdate.accept(new StatusUpdate(id, Status.SUCCESS));

        for (String dependentId : dependencyRegistry.getDependents(id)) {
            dependencyRegistry.removeDependency(dependentId, id);
            if (!dependencyRegistry.hasDependencies(dependentId)) { //all dependencies completed
                if (taskRegistry.contains(dependentId)) {
                    onStatusUpdate.accept(new StatusUpdate(dependentId, Status.READY));
                }
            }
        }
    }

}
