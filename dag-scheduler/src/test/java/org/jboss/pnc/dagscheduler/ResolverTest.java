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

import org.jboss.pnc.dagscheduler.local.DefaultDagRunner;
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
        final DynamicDagResolver resolver = new DefaultDagRunner();

        List<Task> ready = new ArrayList<>();
        Consumer<Task> onTaskReady = task -> {
            logger.info("Ready task: {}.", task.getId());
            ready.add(task);
            resolver.resolveTask(task.getId(), CompletedTask.Status.DONE);
        };

        Consumer<CompletedTask> onTaskCompleted = completedTask -> {

        };

        resolver.setOnReadyListener(onTaskReady);
        resolver.setOnCompleteListener(onTaskCompleted);

        Task<String> a = Task.create("A", "data");
        Task<String> b = Task.create("B", "data", new HashSet<>(Arrays.asList(a)));
        Task<String> c = Task.create("C", "data", new HashSet<>(Arrays.asList(b)));
        Task<String> d = Task.create("D", "data");
        Task<String> e = Task.create("E", "data", new HashSet<>(Arrays.asList(b,d)));

        Assert.assertEquals(2, b.getDependents().size());
        Assert.assertEquals(1, b.getDependencies().size());
        Assert.assertEquals(1, c.getDependencies().size());
        Assert.assertEquals(2, e.getDependencies().size());

        resolver.submitTasks(Arrays.asList(a, b, c, d, e));
        Assert.assertEquals(5, resolver.getCount());

        Assert.assertEquals(5, ready.size());
        Assert.assertTrue(ready.contains(e));
    }

}