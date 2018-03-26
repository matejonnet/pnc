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
import org.jboss.pnc.dagscheduler.DynamicDagResolver;
import org.jboss.pnc.dagscheduler.Task;

import java.io.Serializable;
import java.util.Collection;
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
public class DefaultDagRunner<T extends Serializable> implements DynamicDagResolver<T> {

    private final ConcurrentMap<String, Task<T>> tasks = new ConcurrentHashMap<>();

    private Consumer<Task<T>> onTaskReady = (t) -> {};
    private Consumer<CompletedTask> onTaskCompleted = (t) -> {};

    public DefaultDagRunner() {

    }

    @Override
    public void setOnReadyListener(Consumer<Task<T>> onTaskReady) {
        this.onTaskReady = onTaskReady;
    }

    @Override
    public void setOnCompleteListener(Consumer<CompletedTask> onTaskCompleted) {
        this.onTaskCompleted = onTaskCompleted;
    }

    @Override
    public void submitTasks(Collection<Task<T>> tasks) {
        for (Task<T> task : tasks) {
            submitTask(task);
        }
    }

    @Override
    public void submitTask(Task<T> task) {
        Task existing = tasks.putIfAbsent(task.getId(), task);
        if (existing == null) {
            //check for cycles
            wireDependencies(task);
            if (areDependenciesResolved(task)) {
                onTaskReady.accept(task);
            }
        } else {
            onTaskCompleted.accept(new CompletedTask(task, CompletedTask.Status.ALREADY_SUBMITTED));
        }
    }

    private void wireDependencies(Task<T> task) {
        for (Task dependency : task.getDependencies()) {
            Task<T> submittedTask = tasks.get(dependency.getId());
            if (submittedTask == null) {
                onTaskCompleted.accept(new CompletedTask(task, CompletedTask.Status.MISSING_DEPENENCY));
            } else {
                task.addDependency(submittedTask);
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
        if (status.equals(CompletedTask.Status.DONE)) {
            success(task);
        } else if (status.equals(CompletedTask.Status.ERROR)) {
            fail(task);
        }
    }

    @Override
    public int getCount() {
        return tasks.size(); //TODO can it throw concurrent modification exception ?
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

        for (Task<T> dependent : task.getDependents()) {
            dependent.removeDependency(task);
            if (areDependenciesResolved(dependent)) {
                Task<T> listedDependent = tasks.get(dependent.getId());
                if (listedDependent != null) {
                    onTaskReady.accept(dependent);
                }
            }
        }
    }

    protected boolean areDependenciesResolved(Task task) {
        return task.getDependencies().size() == 0;
    }


    public Set<Task<T>> getAllTaskDependencies(Task<T> task) {
        Set<Task<T>> dependencies = new HashSet<>();
        Stack<Task<T>> stack = new Stack<>();
        stack.add(task);

        while (!stack.isEmpty()) {
            Task poppedTask = stack.pop();
            dependencies.add(poppedTask);
            stack.addAll(poppedTask.getDependencies());
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
            stack.addAll(poppedTask.getDependents());
        }

        return Collections.unmodifiableSet(dependents);
    }

}
