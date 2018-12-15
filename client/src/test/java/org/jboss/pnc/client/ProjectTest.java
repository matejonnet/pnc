/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014-2018 Red Hat, Inc., and individual contributors
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
package org.jboss.pnc.client;

import org.jboss.pnc.dto.Project;
import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class ProjectTest {

    ConnectionInfo connectionInfo = ConnectionInfo.newBuilder()
            .host("locahost")
            .port(8080)
            .build();

    @Test
    public void shouldCreateNewProject() {
        ProjectClient projectClient = new ProjectClient(connectionInfo);

        Project project = Project.builder()
                .projectUrl("https://github.com/entity-ncl/pnc")
                .issueTrackerUrl("https://github.com/entity-ncl/pnc/issues")
                .build();

        Optional<Project> projectReturned = projectClient.createNew(project);
        Assert.assertTrue(projectReturned.isPresent());

        Integer returnedId = projectReturned.get().getId();
        Assert.assertNotNull(returnedId);

        Optional<Project> stored = projectClient.getSpecific(returnedId);
        Assert.assertTrue(stored.isPresent());
        Assert.assertNotNull(stored.get().getName());
    }

    @Test
    public void generatedClassTest() {
//        new org.jboss.pnc.client.example.User("a", "b").
    }
}