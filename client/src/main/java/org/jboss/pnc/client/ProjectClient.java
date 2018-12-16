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

import org.jboss.pnc.dto.Build;
import org.jboss.pnc.dto.BuildConfiguration;
import org.jboss.pnc.dto.Project;
import org.jboss.pnc.dto.response.Page;
import org.jboss.pnc.rest.api.endpoints.ProjectEndpoint;
import org.jboss.pnc.rest.api.parameters.BuildsFilterParameters;
import org.jboss.pnc.rest.api.parameters.PageParameters;

import javax.ws.rs.ClientErrorException;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class ProjectClient extends ClientBase<Project> {


    public ProjectClient(ConnectionInfo connectionInfo) {
        super(connectionInfo);
    }

    protected ProjectEndpoint getEndpoint() {
        return target.proxy(ProjectEndpoint.class);
    }

    public Page<BuildConfiguration> getBuildConfigurations(String id, PageParameters page) throws RemoteResourseReadException {
        try {
            return getEndpoint().getBuildConfigurations(id, page);
        } catch (ClientErrorException e) {
            throw new RemoteResourseReadException("Cannot get remote resource.", e);
        }
    }

    public Page<Build> getBuilds(String id,
            PageParameters page,
            BuildsFilterParameters filter) throws RemoteResourseReadException {
        try {
            return getEndpoint().getBuilds(id, page, filter);
        } catch (ClientErrorException e) {
            throw new RemoteResourseReadException("Cannot get remote resource.", e);
        }
    }


}
