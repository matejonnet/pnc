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

import org.jboss.pnc.dagscheduler.local.DefaultDagResolver;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class ResolverTest {

    private final Logger logger = LoggerFactory.getLogger(ResolverTest.class);

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void shouldEmitAllWhenDependenciesAreResolved() throws Exception {
        final DagResolver<String> resolver = new DefaultDagResolver();

        List<Task> ready = new ArrayList<>();
        Consumer<Task<String>> onTaskReady = task -> {
            logger.info("Ready task: {}.", task.getId());
            ready.add(task);
        };

        Consumer<CompletedTask> onTaskCompleted = completedTask -> {

        };

        resolver.setOnReadyListener(onTaskReady);
        resolver.setOnCompleteListener(onTaskCompleted);

        Task<String> a = resolver.submitTask("A", "data");
        Task<String> b = resolver.submitTask("B", "data", new HashSet<>(Arrays.asList(a)));
        Task<String> c = resolver.submitTask("C", "data", new HashSet<>(Arrays.asList(b)));
        Task<String> d = resolver.submitTask("D", "data");
        Task<String> e = resolver.submitTask("E", "data", new HashSet<>(Arrays.asList(b,d)));

        Assert.assertEquals(1, resolver.getDependents(a).size());
        Assert.assertEquals(1, resolver.getDependencies(b).size());
        Assert.assertEquals(1, resolver.getDependencies(c).size());
        Assert.assertEquals(2, resolver.getDependencies(e).size());
        Assert.assertEquals(5, resolver.getCount());

        resolver.resolveTask(a.getId(), CompletedTask.Status.DONE);
        resolver.resolveTask(b.getId(), CompletedTask.Status.DONE);
        resolver.resolveTask(c.getId(), CompletedTask.Status.DONE);
        resolver.resolveTask(d.getId(), CompletedTask.Status.DONE);

        Assert.assertEquals(5, ready.size());
        Assert.assertTrue(ready.contains(e));
    }

    @Test
    public void shouldDetectCycleDependency() {
        final DagResolver<String> resolver = new DefaultDagResolver();

        List<CompletedTask> cycles = new ArrayList<>();
        Consumer<Task<String>> onTaskReady = task -> {
        };

        Consumer<CompletedTask> onTaskCompleted = completedTask -> {
            if (completedTask.getStatus().equals(CompletedTask.Status.INTRODUCES_CYCLE_DEPENDENCY)) {
                cycles.add(completedTask);
            }
        };

        resolver.setOnReadyListener(onTaskReady);
        resolver.setOnCompleteListener(onTaskCompleted);

        Task<String> a = resolver.submitTask("A", "data");
        Task<String> b = resolver.submitTask("B", "data", new HashSet<>(Arrays.asList(a)));
        Task<String> c = resolver.submitTask("C", "data", new HashSet<>(Arrays.asList(b)));
        Task<String> a1 = resolver.submitTask("A", "data", new HashSet<>(Arrays.asList(c)));

        Assert.assertEquals(1, cycles.size());
        Assert.assertEquals("A", cycles.get(0).getTask().getId());

    }

}