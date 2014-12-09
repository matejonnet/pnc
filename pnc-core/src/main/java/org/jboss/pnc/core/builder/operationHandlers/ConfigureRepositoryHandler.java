package org.jboss.pnc.core.builder.operationHandlers;

import org.jboss.pnc.core.RepositoryManagerFactory;
import org.jboss.pnc.core.builder.BuildQueue;
import org.jboss.pnc.core.builder.BuildTask;
import org.jboss.pnc.core.exception.CoreException;
import org.jboss.pnc.model.BuildCollection;
import org.jboss.pnc.model.ProjectBuildConfiguration;
import org.jboss.pnc.model.RepositoryType;
import org.jboss.pnc.model.TaskStatus;
import org.jboss.pnc.spi.repositorymanager.RepositoryConfiguration;
import org.jboss.pnc.spi.repositorymanager.RepositoryManager;

import javax.inject.Inject;
import java.util.function.Consumer;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2014-12-08.
 */
public class ConfigureRepositoryHandler implements OperationHandler {
    private OperationHandler next;

    @Inject
    BuildQueue buildQueue;

    @Inject
    RepositoryManagerFactory repositoryManagerFactory;

    @Override
    public void handle(BuildTask task) {
        if (task.getStatus().isOperationCompleted(TaskStatus.Operation.NEW)) {
            createRepository(task);
        } else {
            if (next != null) {
                next.handle(task);
            }
        }
    }

    @Override
    public void next(OperationHandler handler) {
        next = handler;
    }

    private void createRepository(BuildTask buildTask) {
        buildTask.onStatusUpdate(new TaskStatus(TaskStatus.Operation.CREATE_REPOSITORY, 0));
        try {
            Consumer<RepositoryConfiguration> onComplete = (repositoryConfiguration) -> {
                buildTask.onStatusUpdate(new TaskStatus(TaskStatus.Operation.CREATE_REPOSITORY, 100));
                buildTask.setRepositoryConfiguration(repositoryConfiguration);
                buildQueue.add(buildTask);
            };

            Consumer<Exception> onError = (e) -> {
                buildTask.onError(e);
            };

            RepositoryManager repositoryManager = repositoryManagerFactory.getRepositoryManager(RepositoryType.MAVEN);
            ProjectBuildConfiguration buildConfiguration = buildTask.getProjectBuildConfiguration();
            BuildCollection buildCollection = buildTask.getBuildCollection();

            //TODO better validation
            assert (buildConfiguration != null);
            assert (buildCollection != null);

            repositoryManager.createRepository(buildConfiguration, buildCollection, onComplete, onError);

        } catch (CoreException e) {
            buildTask.onError(e);
        }
    }

}
