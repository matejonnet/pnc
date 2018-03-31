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
        final DagResolver resolver = new DefaultDagResolver();

        List<String> ready = new ArrayList<>();
        Consumer<String> onTaskReady = taskId -> {
            logger.info("Ready task: {}.", taskId);
            ready.add(taskId);
        };

        Consumer<CompletedTask> onTaskCompleted = completedTask -> {

        };

        resolver.setOnReadyListener(onTaskReady);
        resolver.setOnCompleteListener(onTaskCompleted);

        resolver.submitTask("A");
        resolver.submitTask("B", new HashSet<>(Arrays.asList("A")));
        resolver.submitTask("C", new HashSet<>(Arrays.asList("B")));
        resolver.submitTask("D");
        resolver.submitTask("E", new HashSet<>(Arrays.asList("B", "D")));

        Assert.assertEquals(1, resolver.getDependents("A").size());
        Assert.assertEquals(1, resolver.getDependencies("B").size());
        Assert.assertEquals(1, resolver.getDependencies("C").size());
        Assert.assertEquals(2, resolver.getDependencies("E").size());
        Assert.assertEquals(5, resolver.getCount());

        resolver.resolveTask("A", ResolutionStatus.SUCCESS);
        resolver.resolveTask("B", ResolutionStatus.SUCCESS);
        resolver.resolveTask("C", ResolutionStatus.SUCCESS);
        resolver.resolveTask("D", ResolutionStatus.SUCCESS);

        Assert.assertEquals(5, ready.size());
        Assert.assertTrue(ready.contains("E"));
    }

    @Test
    public void shouldDetectCycleDependency() {
        final DagResolver resolver = new DefaultDagResolver();

        List<CompletedTask> cycles = new ArrayList<>();
        Consumer<String> onTaskReady = taskId -> {
        };

        Consumer<CompletedTask> onTaskCompleted = completedTask -> {
            if (completedTask.getStatus().equals(CompletionStatus.INTRODUCES_CYCLE_DEPENDENCY)) {
                cycles.add(completedTask);
            }
        };

        resolver.setOnReadyListener(onTaskReady);
        resolver.setOnCompleteListener(onTaskCompleted);

        resolver.submitTask("A");
        resolver.submitTask("B", new HashSet<>(Arrays.asList("A")));
        resolver.submitTask("C", new HashSet<>(Arrays.asList("B")));
        resolver.submitTask("A", new HashSet<>(Arrays.asList("C")));

        Assert.assertEquals(1, cycles.size());
        Assert.assertEquals("A", cycles.get(0).getTaskId());

        resolver.submitTask("D1", new HashSet<String>(Arrays.asList("D2")));
        resolver.submitTask("D2", new HashSet<String>(Arrays.asList("D1")));

        Assert.assertEquals(2, cycles.size());
        Assert.assertEquals("D2", cycles.get(1).getTaskId());
    }

}