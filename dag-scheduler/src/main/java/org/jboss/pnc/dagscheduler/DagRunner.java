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
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class DagRunner<T extends Serializable> {

    private final ConcurrentMap<String, Task<T>> tasks = new ConcurrentHashMap<>();

    private final Consumer<Task<T>> onTaskReady;
    private final Consumer<CompletedTask> onTaskCompleted;


    public DagRunner(Consumer<Task<T>> onTaskReady, Consumer<CompletedTask> onTaskCompleted) {
        this.onTaskReady = onTaskReady;
        this.onTaskCompleted = onTaskCompleted;
    }

    public void addTask(Task<T> node) {
        doAddIterative(node);
    }

    private void doAddIterative(Task<T> node) {
        Stack<Task<T>> stack = new Stack<>();
        stack.add(node);

        while (!stack.isEmpty()) {
            Task poppedTask = stack.pop();
            stack.addAll(poppedTask.getDependencies());
            Task putted = tasks.putIfAbsent(poppedTask.getId(), poppedTask);
            if (putted != null) {
                onTaskCompleted.accept(new CompletedTask(poppedTask, CompletedTask.Status.ALREADY_RUNNING));
            }
        }
    }

    public int getCount() {
        return tasks.size();
    }

    public void resolveTask(String id, CompletedTask.Status status) {
        Task task = tasks.get(id);
        resolveTask(task, status);
    }

    public void resolveTask(Task task, CompletedTask.Status status) {
        if (status.equals(CompletedTask.Status.DONE)) {
            complete(task);
        } else if (status.equals(CompletedTask.Status.ERROR)) {
            fail(task);
        }
    }

    private void fail(Task task) {
        tasks.remove(task);
        onTaskCompleted.accept(new CompletedTask(task, CompletedTask.Status.FAILED_DEPENDENCY));

        Stack<Task> stack = new Stack<>();
        stack.addAll(task.getDependents());

        while (!stack.isEmpty()) {
            Task poppedTask = stack.pop();
            stack.addAll(poppedTask.getDependents());
            tasks.remove(poppedTask);
            onTaskCompleted.accept(new CompletedTask(poppedTask, CompletedTask.Status.FAILED_DEPENDENCY));
        }
    }

    private void complete(Task<T> task) {
        tasks.remove(task);

        onTaskCompleted.accept(new CompletedTask(task, CompletedTask.Status.DONE));
        Optional<Boolean> anyResolved = task.getDependents().stream()
                .map(dependent -> dependent.removeDependency(task))
                .filter(e -> e == true)
                .findAny();

        if (anyResolved.isPresent()) {
            for (Task ready : getReadyTasks()) {
                onTaskReady.accept(ready);
            }
        }

    }

    private Set<Task> getReadyTasks() {
        return tasks.values().stream()
            .filter(task -> task.getDependencies().size() == 0)
            .collect(Collectors.toSet());
    }

}
