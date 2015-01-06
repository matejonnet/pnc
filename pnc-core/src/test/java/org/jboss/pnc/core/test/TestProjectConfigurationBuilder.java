package org.jboss.pnc.core.test;

import org.jboss.pnc.model.Environment;
import org.jboss.pnc.model.Project;
import org.jboss.pnc.model.ProjectBuildConfiguration;
import org.jboss.pnc.model.builder.EnvironmentBuilder;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2014-12-10.
 */
public class TestProjectConfigurationBuilder {

    Environment javaEnvironment = EnvironmentBuilder.defaultEnvironment().build();

    public ProjectBuildConfiguration buildConfigurationWhichDependsOnItself() {
        ProjectBuildConfiguration projectBuildConfiguration = build(1, "depends-on-itself");
        projectBuildConfiguration.addDependency(projectBuildConfiguration);
        return projectBuildConfiguration;
    }

    public ProjectBuildConfiguration buildConfigurationWithCycleDependency() {
        ProjectBuildConfiguration projectBuildConfiguration1 = build(1, "cycle-dependency-1");
        ProjectBuildConfiguration projectBuildConfiguration2 = build(2, "cycle-dependency-2");
        ProjectBuildConfiguration projectBuildConfiguration3 = build(3, "cycle-dependency-3");

        projectBuildConfiguration1.addDependency(projectBuildConfiguration2);
        projectBuildConfiguration2.addDependency(projectBuildConfiguration3);
        projectBuildConfiguration3.addDependency(projectBuildConfiguration1);

        return projectBuildConfiguration1;
    }

    public ProjectBuildConfiguration build(int id, String name) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        ProjectBuildConfiguration projectBuildConfiguration = new ProjectBuildConfiguration();
        projectBuildConfiguration.setId(id);
        projectBuildConfiguration.setIdentifier(id + "");
        projectBuildConfiguration.setEnvironment(javaEnvironment);
        projectBuildConfiguration.setProject(project);
        project.addProjectBuildConfiguration(projectBuildConfiguration);
        return projectBuildConfiguration;
    }

}
