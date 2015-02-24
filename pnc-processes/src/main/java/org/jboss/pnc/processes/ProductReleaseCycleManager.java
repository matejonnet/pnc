package org.jboss.pnc.processes;

import org.jboss.pnc.common.util.StreamCollectors;
import org.jboss.pnc.model.Product;
import org.jboss.pnc.processes.tasks.BasicProductConfiguration;
import org.jboss.pnc.processes.tasks.UserTask;
import org.jboss.pnc.spi.datastore.Datastore;
import org.jboss.util.collection.WeakSet;
import org.kie.api.event.process.DefaultProcessEventListener;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkflowProcessInstance;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.runtime.manager.cdi.qualifier.Singleton;

import javax.inject.Inject;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-16.
 */
public class ProductReleaseCycleManager {

    public static final String PROCESS_ID = "org.jboss.pnc.defaultbuild2";
    public static final String LANGUAGE = "en-UK";

    public static final String PRODUCT_ID = "productId";

    @Inject
    @Singleton
    RuntimeManager runtimeManager;

    @Inject
    KieSession  kieSession;

    @Inject
    TaskService taskService;

    @Inject
    Datastore datastore;

//    @Inject
//    TransactionManager transactionManager;

    private Set<TaskStatusListener> statusUpdateListeners = new WeakSet();

    /**
     *
     * @param basicProductConfiguration
     * @return null if product configuration is not valid
     */
    public Product startNewProductRelease(BasicProductConfiguration basicProductConfiguration)
            throws HeuristicRollbackException, RollbackException, HeuristicMixedException, SystemException, NotSupportedException {
        if (basicProductConfiguration.isValid()) {
            //TODO Product product = datastore.createNewProduct(basicProductConfiguration);
            Product product = new Product();
            Integer productId = 1;
            product.setId(productId);

//            transactionManager.begin();

            ProcessInstance pi = kieSession.startProcess(PROCESS_ID, basicProductConfiguration.getProcessParams());
            ProcessInstance processInstance = kieSession.getProcessInstance(pi.getId());

            ((WorkflowProcessInstance) processInstance).setVariable(PRODUCT_ID, productId);

            kieSession.addEventListener(new TaskCompleteListener());

//            transactionManager.commit();
            return product;
        } else {
            return null;
        }
    }

    public ProcessInstance getProcessInstance(Integer productId) {

        Predicate<ProcessInstance> findProcess = (ProcessInstance processInstance) -> {
            Integer piProductId = (Integer) ((WorkflowProcessInstance) processInstance).getVariable(PRODUCT_ID);
            return productId.equals(piProductId);
        };

        return kieSession.getProcessInstances().stream().filter(findProcess).collect(StreamCollectors.singletonCollector());
    }

    private int getProductId(ProcessInstance processInstance) {
        return (int) ((WorkflowProcessInstance) processInstance).getVariable(PRODUCT_ID); //TODO check for null
    }

    public List<TaskSummary> getAllTasks(ProcessInstance processInstance) {
        List<Status> all = new ArrayList<>(Arrays.asList(Status.values()));
        return taskService.getTasksByStatusByProcessInstanceId(processInstance.getId(), all, LANGUAGE);

    }

    public TaskStatus getTaskStatus(Integer productId, long taskId) {
        TaskSummary taskSummary = getTaskById(productId, taskId);
        if (taskSummary != null) {
            return TaskStatus.fromStatus(taskSummary.getStatus());
        } else {
            return null;
        }
    }

    private TaskSummary getTaskById(Integer productId, long taskId) {
        ProcessInstance processInstance = getProcessInstance(productId);
        if (processInstance != null) {
            List<TaskSummary> allTasks = getAllTasks(processInstance);
            return allTasks.stream().filter(t -> t.getId() == taskId)
                    .collect(StreamCollectors.singletonCollector());
        } else {
            return null;
        }
    }

    public void registerTaskStatusUpdateListener(TaskStatusListener taskStatusListener) {
        statusUpdateListeners.add(taskStatusListener);
    }

    public void unregisterTaskStatusUpdateListener(TaskStatusListener taskStatusListener) {
        statusUpdateListeners.remove(taskStatusListener);
    }

    private void notifyTaskStatusUpdated(Integer productId, long taskId) {
        TaskSummary taskById = getTaskById(productId, taskId);
        Status newStatus = taskById.getStatus();
        List<TaskStatusListener> taskListeners = statusUpdateListeners.stream().filter(l -> l.getTaskId() == taskId)
                .collect(Collectors.toList());
        taskListeners.forEach(l -> l.onStatusUpdate(TaskStatus.fromStatus(newStatus)));
    }

    public void startUserTask(UserTask userTask) {
        Integer productId = userTask.getProductId();
        Integer taskId = userTask.getTaskId();
        TaskSummary task = getTaskById(productId, taskId);
        userTask.setTask(task);
        userTask.setTaskService(taskService);
        taskService.start(taskId, userTask.getUser().getUsername());
    }

    class TaskCompleteListener extends DefaultProcessEventListener {
        @Override
        public void afterNodeLeft(ProcessNodeLeftEvent event) {
            long taskId = event.getNodeInstance().getId();
            int productId = getProductId(event.getProcessInstance());
            notifyTaskStatusUpdated(productId, taskId);
        }
    }

}
