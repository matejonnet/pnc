package org.jboss.pnc.processes.tasks;

import org.jboss.pnc.model.User;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-20.
 */
public class ConfigureRepository extends BaseUserTask implements UserTask {

    public static final String REPOSITORY_CONFIGURATION_KEY = "REPOSITORY_CONFIGURATION";
    private RepositoryConfiguration configuration;

    public ConfigureRepository(Integer taskId, Integer productId, User user) {
        super(taskId, productId, user);
    }

    public void completeTask(RepositoryConfiguration repositoryConfiguration) {
        Map<String, Object> data = new HashMap<String, Object>() {{
            put (REPOSITORY_CONFIGURATION_KEY, repositoryConfiguration);
        }};
        taskService.complete(taskId, user.getUsername(), data);
    }

    public void setConfiguration(RepositoryConfiguration configuration) {
        this.configuration = configuration;
    }

}
