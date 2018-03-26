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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class GraphTest {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void addTask() throws Exception {
        List<Task> ready = new ArrayList<>();
        Consumer<Task> onTaskReady = task -> {
            ready.add(task);
        };
        DagRunner runner = new DagRunner(onTaskReady, onTaskCompleted);

        Task a = Task.create("A");
        Task b = Task.create("B", a);
        runner.addTask(b);

        Assert.assertEquals(2, runner.getCount());
        Assert.assertEquals(1, a.getDependents().size());
        Assert.assertEquals(1, b.getDependencies().size());

        runner.resolveTask("A", Task.Status.DONE);

        Assert.assertTrue(ready.contains(b));
    }

}