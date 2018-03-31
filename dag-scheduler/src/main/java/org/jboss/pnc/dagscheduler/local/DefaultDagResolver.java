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

import org.jboss.pnc.dagscheduler.CompletedTask;
import org.jboss.pnc.dagscheduler.CompletionStatus;
import org.jboss.pnc.dagscheduler.DagResolver;
import org.jboss.pnc.dagscheduler.DependencyRegistry;
import org.jboss.pnc.dagscheduler.ResolutionStatus;
import org.jboss.pnc.dagscheduler.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class DefaultDagResolver<T extends Serializable> implements DagResolver<T> {

    private final Logger logger = LoggerFactory.getLogger(DefaultDagResolver.class);

    private final ConcurrentMap<String, Task<T>> tasks = new ConcurrentHashMap<>();
    private final DependencyRegistry dependencyRegistry = new DefaultDependencyRegistry();

    private Consumer<String> onTaskReady = (t) -> {};
    private Consumer<CompletedTask> onTaskCompleted = (t) -> {};

    public DefaultDagResolver() {

    }

    @Override
    public void setOnReadyListener(Consumer<String> onTaskReady) {
        this.onTaskReady = onTaskReady;
    }

    @Override
    public void setOnCompleteListener(Consumer<CompletedTask> onTaskCompleted) {
        this.onTaskCompleted = onTaskCompleted;
    }

    @Override
    public Task<T> submitTask(String id, T data, Set<String> dependencies) {
        logger.debug("Adding new task {} ...", id);
        Task<T> task = new Task(id, data);

        if (introducesCycle(id, dependencies)) {
            onTaskCompleted.accept(new CompletedTask(id, CompletionStatus.INTRODUCES_CYCLE_DEPENDENCY));
            return task;
        }

        for (String dependency : dependencies) {
            dependencyRegistry.addDependency(id, dependency);
        }

        Task existing = tasks.get(id);
        if (existing == null) {
            if (introducesCycle(id, dependencies)) {
                onTaskCompleted.accept(new CompletedTask(id, CompletionStatus.INTRODUCES_CYCLE_DEPENDENCY));
                return task;
            }
            tasks.put(id, task);
            wireDependencies(id);
            if (!dependencyRegistry.hasDependencies(task.getId())) { //all dependencies completed
                onTaskReady.accept(id);
            }
        } else {
            onTaskCompleted.accept(new CompletedTask(id, CompletionStatus.ALREADY_SUBMITTED));
        }
        return task;
    }

    private boolean introducesCycle(String taskId, Set<String> dependencies) {
        Set<String> allTaskDependents = getDependents(taskId);
        // check if any dependent depends on any dependency
        for (String dependency : dependencies) {
            for (String transitiveDependency : getAllTaskDependencies(dependency)) {
                if (allTaskDependents.contains(transitiveDependency)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void wireDependencies(String taskId) {
        for (String dependencyId : dependencyRegistry.getDependencies(taskId)) {
            Task<T> submittedTask = tasks.get(dependencyId);
            if (submittedTask == null) {
                onTaskCompleted.accept(new CompletedTask(taskId, CompletionStatus.MISSING_DEPENDENCY)); //TODO is it allowed to submit the dependency latter ?
            } else {
                dependencyRegistry.addDependency(taskId, submittedTask.getId());
            }
        }
    }

    @Override
    public void resolveTask(String taskId, ResolutionStatus resolutionStatus) {
        logger.debug("Resolving task {} with status {}.", taskId, resolutionStatus);
        if (resolutionStatus.isSuccess()) {
            success(taskId);
        } else {
            fail(taskId, resolutionStatus);
        }
    }

    @Override
    public int getCount() {
        return tasks.size();
    }

    @Override
    public Set<String> getDependencies(String taskId) {
        return dependencyRegistry.getDependencies(taskId);
    }

    @Override
    public Set<String> getDependents(String taskId) {
        return dependencyRegistry.getDependents(taskId);
    }

    private void fail(String taskId, ResolutionStatus resolutionStatus) {
        tasks.remove(taskId);
        onTaskCompleted.accept(new CompletedTask(taskId, resolutionStatus.toCompletionStatus()));

        for (String dependentId : getAllTaskDependents(taskId)) {
            Task<T> listedDependent = tasks.get(dependentId);
            if (listedDependent != null) {
                tasks.remove(listedDependent.getId());
                onTaskCompleted.accept(new CompletedTask(listedDependent.getId(), CompletionStatus.FAILED_DEPENDENCY));
            }
        }
    }

    private void success(String taskId) {
        tasks.remove(taskId);
        onTaskCompleted.accept(new CompletedTask(taskId, CompletionStatus.SUCCESS));

        for (String dependentId : dependencyRegistry.getDependents(taskId)) {
            dependencyRegistry.removeDependency(dependentId, taskId);
            if (!dependencyRegistry.hasDependencies(dependentId)) { //all dependencies completed
                Task<T> listedDependent = tasks.get(dependentId);
                if (listedDependent != null) {
                    onTaskReady.accept(tasks.get(dependentId).getId());
                }
            }
        }
    }

    public Set<String> getAllTaskDependencies(String taskId) {
        Set<String> dependencies = new HashSet<>();
        Stack<String> stack = new Stack<>();
        stack.add(taskId);

        while (!stack.isEmpty()) {
            String poppedTaskId = stack.pop();
            dependencies.add(poppedTaskId);
            Set<String> poppedTaskDependencies = dependencyRegistry.getDependencies(poppedTaskId);

            for (String poppedTaskDependency : poppedTaskDependencies) {
                if (dependencies.contains(poppedTaskDependency)) {
                    throw new RuntimeException("Task " + taskId + " is introducing cyclic dependencies!");
                }
            }
            stack.addAll(poppedTaskDependencies);
        }

        return Collections.unmodifiableSet(dependencies);
    }


    public Set<String> getAllTaskDependents(String taskId) {
        Set<String> dependents = new HashSet<>();
        Stack<String> stack = new Stack<>();
        stack.add(taskId);

        while (!stack.isEmpty()) {
            String poppedTaskId = stack.pop();
            dependents.add(poppedTaskId);
            stack.addAll(dependencyRegistry.getDependencies(poppedTaskId));
        }

        return Collections.unmodifiableSet(dependents);
    }

}
