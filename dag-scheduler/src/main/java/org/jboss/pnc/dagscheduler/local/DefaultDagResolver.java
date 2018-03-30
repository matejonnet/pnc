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
import org.jboss.pnc.dagscheduler.DagResolver;
import org.jboss.pnc.dagscheduler.DependencyRegistry;
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
    private final DependencyRegistry<T> dependencyRegistry = new DefaultDependencyRegistry();

    private Consumer<Task<T>> onTaskReady = (t) -> {};
    private Consumer<CompletedTask> onTaskCompleted = (t) -> {};

    public DefaultDagResolver() {

    }

    @Override
    public void setOnReadyListener(Consumer<Task<T>> onTaskReady) {
        this.onTaskReady = onTaskReady;
    }

    @Override
    public void setOnCompleteListener(Consumer<CompletedTask> onTaskCompleted) {
        this.onTaskCompleted = onTaskCompleted;
    }

//    @Override
//    public void submitTasks(Collection<Task<T>> tasks) {
//        for (Task<T> task : tasks) {
//            submitTask(task);
//        }
//    }

    @Override
    public Task<T> submitTask(String id, T data, Set<Task<T>> dependencies) {
        logger.debug("Adding new task {} ...", id);
        Task<T> task = new Task(id, data);

        if (introducesCycle(task, dependencies)) { //TODO
            onTaskCompleted.accept(new CompletedTask(task, CompletedTask.Status.INTRODUCES_CYCLE_DEPENDENCY));
            return task;
        }

        for (Task<T> dependency : dependencies) {
            dependencyRegistry.addDependency(task, dependency);
        }

        Task existing = tasks.get(task.getId());
        if (existing == null) {
            if (introducesCycle(task, dependencies)) {
                onTaskCompleted.accept(new CompletedTask(task, CompletedTask.Status.INTRODUCES_CYCLE_DEPENDENCY));
                return task;
            }
            tasks.put(task.getId(), task);
            wireDependencies(task);
            if (!dependencyRegistry.hasDependencies(task)) { //all dependencies completed
                onTaskReady.accept(task);
            }
        } else {
            onTaskCompleted.accept(new CompletedTask(task, CompletedTask.Status.ALREADY_SUBMITTED));
        }
        return task;
    }

    private boolean introducesCycle(Task<T> task, Set<Task<T>> dependencies) {
        Set<Task<T>> allTaskDependents = getDependents(task);
        // check if any dependent depends on any dependency
        for (Task<T> dependency : dependencies) {
            for (Task<T> transitiveDependency : getAllTaskDependencies(dependency)) {
                if (allTaskDependents.contains(transitiveDependency)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void wireDependencies(Task<T> task) {
        for (Task<T> dependency : dependencyRegistry.getDependencies(task)) {
            Task<T> submittedTask = tasks.get(dependency.getId());
            if (submittedTask == null) {
                onTaskCompleted.accept(new CompletedTask(task, CompletedTask.Status.MISSING_DEPENDENCY));
            } else {
                dependencyRegistry.addDependency(task, submittedTask);
            }
        }
    }

    @Override
    public void resolveTask(String id, CompletedTask.Status status) {
        Task task = tasks.get(id);
        resolveTask(task, status);
    }

    @Override
    public void resolveTask(Task task, CompletedTask.Status status) {
        logger.debug("Resolving task {} with status {}.", task, status);
        if (status.equals(CompletedTask.Status.DONE)) {
            success(task);
        } else if (status.equals(CompletedTask.Status.ERROR)) {
            fail(task);
        }
    }

    @Override
    public int getCount() {
        return tasks.size();
    }

    @Override
    public Set<Task<T>> getDependencies(Task<T> task) {
        return dependencyRegistry.getDependencies(task);
    }

    @Override
    public Set<Task<T>> getDependents(Task<T> task) {
        return dependencyRegistry.getDependents(task);
    }

    private void fail(Task<T> task) {
        tasks.remove(task);
        onTaskCompleted.accept(new CompletedTask(task, CompletedTask.Status.FAILED));

        for (Task<T> dependent : getAllTaskDependents(task)) {
            Task<T> listedDependent = tasks.get(dependent.getId());
            if (listedDependent != null) {
                tasks.remove(listedDependent);
                onTaskCompleted.accept(new CompletedTask(listedDependent, CompletedTask.Status.FAILED_DEPENDENCY));
            }
        }
    }

    private void success(Task<T> task) {
        tasks.remove(task);
        onTaskCompleted.accept(new CompletedTask(task, CompletedTask.Status.DONE));

        for (Task<T> dependent : dependencyRegistry.getDependents(task)) {
            dependencyRegistry.removeDependency(dependent, task);
            if (!dependencyRegistry.hasDependencies(dependent)) { //all dependencies completed
                Task<T> listedDependent = tasks.get(dependent.getId());
                if (listedDependent != null) {
                    onTaskReady.accept(dependent);
                }
            }
        }
    }

    public Set<Task<T>> getAllTaskDependencies(Task<T> task) {
        Set<Task<T>> dependencies = new HashSet<>();
        Stack<Task<T>> stack = new Stack<>();
        stack.add(task);

        while (!stack.isEmpty()) {
            Task poppedTask = stack.pop();
            dependencies.add(poppedTask);
            Set<Task<T>> poppedTaskDependencies = dependencyRegistry.getDependencies(poppedTask);

            for (Task<T> poppedTaskDependency : poppedTaskDependencies) {
                if (dependencies.contains(poppedTaskDependency)) {
                    throw new RuntimeException("Task " + task + " is introducing cyclic dependencies!");
                }
            }
            stack.addAll(poppedTaskDependencies);
        }

        return Collections.unmodifiableSet(dependencies);
    }


    public Set<Task<T>> getAllTaskDependents(Task<T> task) {
        Set<Task<T>> dependents = new HashSet<>();
        Stack<Task<T>> stack = new Stack<>();
        stack.add(task);

        while (!stack.isEmpty()) {
            Task poppedTask = stack.pop();
            dependents.add(poppedTask);
            stack.addAll(dependencyRegistry.getDependencies(poppedTask));
        }

        return Collections.unmodifiableSet(dependents);
    }

}
