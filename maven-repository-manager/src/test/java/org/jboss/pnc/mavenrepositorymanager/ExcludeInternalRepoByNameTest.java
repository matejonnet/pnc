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
package org.jboss.pnc.mavenrepositorymanager;

import org.apache.commons.io.IOUtils;
import org.commonjava.indy.client.core.Indy;
import org.commonjava.indy.client.core.util.UrlUtils;
import org.commonjava.indy.model.core.Group;
import org.commonjava.indy.model.core.RemoteRepository;
import org.commonjava.indy.model.core.StoreKey;
import org.commonjava.indy.model.core.StoreType;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleArtifactRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.jboss.pnc.mavenrepositorymanager.fixture.TestBuildExecution;
import org.jboss.pnc.model.Artifact;
import org.jboss.pnc.spi.repositorymanager.BuildExecution;
import org.jboss.pnc.spi.repositorymanager.RepositoryManagerResult;
import org.jboss.pnc.spi.repositorymanager.model.RepositorySession;
import org.jboss.pnc.test.category.ContainerTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.InputStream;
import java.util.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

@Category(ContainerTest.class)
public class ExcludeInternalRepoByNameTest
 extends AbstractImportTest
{

    private static final String INTERNAL = "internal";
    private static final String EXTERNAL = "external";

    @Override
    protected String getInternalRepoPatterns() {
        return INTERNAL;
    }

    @Test
    public void extractBuildArtifacts_ContainsTwoDownloads() throws Exception {
        // create a remote repo pointing at our server fixture's 'repo/test' directory.
        indy.stores().create(new RemoteRepository(INTERNAL, server.formatUrl(INTERNAL)), "Creating internal test remote repo",
                RemoteRepository.class);

        indy.stores().create(new RemoteRepository(EXTERNAL, server.formatUrl(EXTERNAL)), "Creating external test remote repo",
                RemoteRepository.class);

        Group publicGroup = indy.stores().load(StoreType.group, PUBLIC, Group.class);
        if (publicGroup == null) {
            publicGroup = new Group(PUBLIC, new StoreKey(StoreType.remote, INTERNAL), new StoreKey(StoreType.remote, EXTERNAL));
            indy.stores().create(publicGroup, "creating public group", Group.class);
        } else {
            publicGroup.setConstituents(Arrays.asList(new StoreKey(StoreType.remote, INTERNAL), new StoreKey(StoreType.remote, EXTERNAL)));
            indy.stores().update(publicGroup, "adding test remotes to public group");
        }

        String internalPath = "org/foo/internal/1.0/internal-1.0.pom";
        String externalPath = "org/foo/external/1.1/external-1.1.pom";

        String content = "This is a test " + System.currentTimeMillis();

        // setup the expectation that the remote repo pointing at this server will request this file...and define its content.
        server.expect(server.formatUrl(INTERNAL, internalPath), 200, content);
        server.expect(server.formatUrl(EXTERNAL, externalPath), 200, content);

        // create a dummy non-chained build execution and repo session based on it
        BuildExecution execution = new TestBuildExecution();

        RepositorySession rc = driver.createBuildRepository(execution);
        assertThat(rc, notNullValue());

        String baseUrl = rc.getConnectionInfo().getDependencyUrl();

        // download the two files via the repo session's dependency URL, which will proxy the test http server
        // using the expectations above
        assertThat(download(UrlUtils.buildUrl(baseUrl, internalPath)), equalTo(content));
        assertThat(download(UrlUtils.buildUrl(baseUrl, externalPath)), equalTo(content));

        // extract the build artifacts, which should contain the two imported deps.
        // This will also trigger promoting imported artifacts into the shared-imports hosted repo
        RepositoryManagerResult repositoryManagerResult = rc.extractBuildArtifacts();

        List<Artifact> deps = repositoryManagerResult.getDependencies();
        System.out.println(deps);

        assertThat(deps, notNullValue());
        assertThat(deps.size(), equalTo(2));

        Indy indy = driver.getIndy();

        // check that the imports from external locations are available from shared-imports
        InputStream stream = indy.content().get(StoreType.hosted, SHARED_IMPORTS, externalPath);
        String downloaded = IOUtils.toString(stream);
        assertThat(downloaded, equalTo(content));
        stream.close();

        // check that the imports from internal/trusted locations are NOT available from shared-imports
        stream = indy.content().get(StoreType.hosted, SHARED_IMPORTS, internalPath);
        assertThat(stream, nullValue());

    }

}
