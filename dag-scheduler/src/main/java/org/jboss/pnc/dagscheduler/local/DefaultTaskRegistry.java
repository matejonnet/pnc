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

import org.jboss.pnc.dagscheduler.TaskRegistry;
import org.jboss.util.collection.ConcurrentSet;

import java.util.Set;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class DefaultTaskRegistry implements TaskRegistry {

    private final Set<String> tasks = new ConcurrentSet<>();

    @Override
    public boolean contains(String id) {
        return tasks.contains(id);
    }

    @Override
    public void add(String id) {
        tasks.add(id);
    }

    @Override
    public void remove(String id) {
        tasks.remove(id);
    }

    @Override
    public int size() {
        return tasks.size();
    }
}
