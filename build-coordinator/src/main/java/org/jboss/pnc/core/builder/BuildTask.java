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
package org.jboss.pnc.core.builder;

import org.jboss.pnc.core.events.DefaultBuildStatusChangedEvent;
import org.jboss.pnc.model.BuildConfiguration;
import org.jboss.pnc.model.BuildConfigurationAudited;
import org.jboss.pnc.model.ProductVersion;
import org.jboss.pnc.model.User;
import org.jboss.pnc.spi.BuildExecution;
import org.jboss.pnc.spi.BuildExecutionType;
import org.jboss.pnc.spi.BuildStatus;
import org.jboss.pnc.spi.events.BuildStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.event.Event;
import java.net.URI;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
* Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2014-12-23.
*/
public class BuildTask implements BuildExecution {

    public static final Logger log = LoggerFactory.getLogger(BuildTask.class);

    private Integer id;
    private BuildConfiguration buildConfiguration;
    private BuildConfigurationAudited buildConfigurationAudited;
    private User user;
    private Date startTime;
    private Date endTime;

    private BuildExecutionType buildTaskType;
    private BuildStatus status = BuildStatus.NEW;
    private String statusDescription;

    private Event<BuildStatusChangedEvent> buildStatusChangedEvent;

    /**
     * A list of builds waiting for this build to complete.
     */
    private Set<BuildTask> dependants = new HashSet<>();

    /**
     * The builds which must be completed before this build can start
     */
    private Set<BuildTask> dependencies = new HashSet<>();

    private DefaultBuildSetCoordinator buildSetCoordinator;

    private String topContentId;

    private String buildSetContentId;

    private String buildContentId;

    private BuildSetTask buildSetTask;

    private Set<Integer> buildRecordSetIds = new HashSet<>();

    private final AtomicReference<URI> logsWebSocketLink = new AtomicReference<>();
    private boolean hasFailed = false;

    BuildTask(DefaultBuildSetCoordinator buildSetCoordinator,
            BuildConfiguration buildConfiguration, 
            BuildConfigurationAudited buildConfigurationAudited,
            String topContentId,
              String buildSetContentId,
              String buildContentId, 
              BuildExecutionType buildTaskType, 
              User user, 
              BuildSetTask buildSetTask,
              int id) {

        this.buildSetCoordinator = buildSetCoordinator;
        this.id = id;
        this.buildConfiguration = buildConfiguration;
        this.buildConfigurationAudited = buildConfigurationAudited;
        this.user = user;

        this.buildTaskType = buildTaskType;
        this.buildStatusChangedEvent = buildSetCoordinator.getBuildStatusChangedEventNotifier();
        this.topContentId = topContentId;
        this.buildSetContentId = buildSetContentId;
        this.buildContentId = buildContentId;
        this.buildSetTask = buildSetTask;

        if (buildSetTask.getProductMilestone() != null) {
            buildRecordSetIds.add(buildSetTask.getProductMilestone().getPerformedBuildRecordSet().getId());
        }
        if (buildConfiguration.getProductVersions() != null) {
            for (ProductVersion productVersion : buildConfiguration.getProductVersions()) {
                if (productVersion.getCurrentProductMilestone() != null) {
                    buildRecordSetIds.add(productVersion.getCurrentProductMilestone().getPerformedBuildRecordSet().getId());
                }
            }
        }

    }

    public void setStatus(BuildStatus status) {
        BuildStatus oldStatus = this.status;
        this.status = status;
        if (status.hasFailed()) {
            setHasFailed(true);
        }
        Integer userId = Optional.ofNullable(getUser()).map(user -> user.getId()).orElse(null);
        BuildStatusChangedEvent buildStatusChanged = new DefaultBuildStatusChangedEvent(oldStatus, status, getId(),
                buildConfigurationAudited.getId().getId(), userId);
        log.debug("Updating build task {} status to {}", this.getId(), buildStatusChanged);
        buildSetTask.taskStatusUpdated(buildStatusChanged);
        buildStatusChangedEvent.fire(buildStatusChanged);
        if (status.isCompleted()) {
            dependants.forEach((dep) -> dep.requiredBuildCompleted(this));
        }
    }

    public Set<Integer> getBuildRecordSetIds() {
        return buildRecordSetIds;
    }

    public Set<BuildTask> getDependencies() {
        return dependencies;
    }

    public void addDependency(BuildTask buildTask) {
        if (!dependencies.contains(buildTask)) {
            dependencies.add(buildTask);
            buildTask.addDependant(this);
        }
    }

    private void requiredBuildCompleted(BuildTask completed) {
        if (dependencies.contains(completed) && completed.hasFailed()) {
            this.setStatus(BuildStatus.REJECTED);
        } else if (dependencies.stream().allMatch(dep -> dep.getStatus().isCompleted())) {
            buildSetCoordinator.fireBuild(this);
        }
    }

    /**
     * @return current status
     */
    public BuildStatus getStatus() {
        return status;
    }

    /**
     * @return Description of current status. Eg. WAITING: there is no available executor; FAILED: exceptionMessage
     */
    public String getStatusDescription() {
        return statusDescription;
    }

    public BuildConfiguration getBuildConfiguration() {
        return buildConfiguration;
    }

    public BuildConfigurationAudited getBuildConfigurationAudited() {
        return buildConfigurationAudited;
    }

    public Set<BuildConfiguration> getBuildConfigurationDependencies() {
        return buildConfiguration.getDependencies();
    }

    @Override
    public String getTopContentId() {
        return topContentId;
    }

    @Override
    public String getBuildSetContentId() {
        return buildSetContentId;
    }

    @Override
    public String getBuildContentId() {
        return buildContentId;
    }

    public void addDependant(BuildTask buildTask) {
        if (!dependants.contains(buildTask)) {
            dependants.add(buildTask);
            buildTask.addDependency(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BuildTask buildTask = (BuildTask) o;

        return buildConfigurationAudited.equals(buildTask.getBuildConfigurationAudited());

    }

    @Override
    public int hashCode() {
        return buildConfigurationAudited.hashCode();
    }

    void setStatusDescription(String statusDescription) {
        this.statusDescription = statusDescription;
    }

    public boolean hasFailed(){
        return this.hasFailed;
    }

    public void setHasFailed(boolean hasFailed){
       this.hasFailed = hasFailed;
    }

    public int getId() {
        return id;
    }

    @Override
    public String getProjectName() {
        return buildConfigurationAudited.getProject().getName();
    }

    public BuildExecutionType getBuildExecutionType() {
        return buildTaskType;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public User getUser() {
        return user;
    }

    public BuildSetTask getBuildSetTask() {
        return buildSetTask;
    }

    /**
     * Check if this build is ready to build, for example if all dependency builds
     * are complete.
     * 
     * @return
     */
    public boolean readyToBuild() {
        for (BuildTask buildTask : dependencies) {
            if(!buildTask.getStatus().isCompleted()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setLogsWebSocketLink(URI link) {
        this.logsWebSocketLink.set(link);
    }

    @Override
    public void clearLogsWebSocketLink() {
        this.logsWebSocketLink.set(null);
    }

    @Override
    public Optional<URI> getLogsWebSocketLink() {
        return Optional.ofNullable(logsWebSocketLink.get());
    }

    @Override
    public String toString() {
        return "Build Task id:" + id + ", name: " + buildConfigurationAudited.getName() + ", status: " + status;
    }
}
