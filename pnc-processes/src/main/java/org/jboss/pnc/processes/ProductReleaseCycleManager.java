package org.jboss.pnc.processes;

import org.jboss.pnc.common.util.StreamCollectors;
import org.jboss.pnc.model.Product;
import org.jboss.pnc.processes.engine.Runtime;
import org.jboss.pnc.processes.tasks.BasicProductConfiguration;
import org.jboss.pnc.processes.tasks.UserTask;
import org.jboss.util.collection.WeakSet;
import org.kie.api.event.process.DefaultProcessEventListener;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkflowProcessInstance;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.KieInternalServices;
import org.kie.internal.process.CorrelationAwareProcessRuntime;
import org.kie.internal.process.CorrelationKey;
import org.kie.internal.process.CorrelationKeyFactory;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-16.
 */
//@Stateless
public class ProductReleaseCycleManager {

    public static final String PROCESS_ID = "org.jboss.pnc.defaultbuild2";
    public static final String LANGUAGE = "en-UK";

    public static final String PRODUCT_ID = "productId";

//    @Inject
//    Datastore datastore;

    @Resource(mappedName = "java:jboss/TransactionManager")
    private TransactionManager transactionManager;


    @Inject
    Runtime runtime;

    private Set<TaskStatusListener> statusUpdateListeners = new WeakSet();

    /**
     *
     * @param basicProductConfiguration
     * @return null if product configuration is not valid
     */
    public Product startNewProductRelease(BasicProductConfiguration basicProductConfiguration) throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        if (basicProductConfiguration.isValid()) {

            //TODO Product product = datastore.createNewProduct(basicProductConfiguration);
            Product product = new Product();
            Integer productId = 1;
            product.setId(productId);

            KieSession kieSession = getKieSession();
            transactionManager.begin(); //TODO transaction boundary must be after getKieSession();

            ProcessInstance processInstance = ((CorrelationAwareProcessRuntime)kieSession).startProcess(
                    PROCESS_ID,
                    getCorrelationKey(productId),
                    basicProductConfiguration.getProcessParams());

            kieSession.addEventListener(new TaskCompleteListener());

            getAllTasks(processInstance);

            transactionManager.commit();
            return product;
        } else {
            return null;
        }
    }

    private CorrelationKey getCorrelationKey(Integer productId) {
        CorrelationKeyFactory factory = KieInternalServices.Factory.get().newCorrelationKeyFactory();
        return factory.newCorrelationKey(productId.toString());
    }

    private KieSession getKieSession() {
        return runtime.getKieSession();
    }

    private TaskService getTaskService() {
        return runtime.getTaskService();
    }


    public ProcessInstance getProcessInstance(Integer productId) throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        transactionManager.begin(); //TODO transaction boundary must be after getKieSession();
        KieSession kieSession = getKieSession();
        ProcessInstance processInstance = ((CorrelationAwareProcessRuntime) kieSession).getProcessInstance(getCorrelationKey(productId));
        transactionManager.commit();
        return processInstance;
    }

    private int getProductId(ProcessInstance processInstance) {
        return (int) ((WorkflowProcessInstance) processInstance).getVariable(PRODUCT_ID); //TODO check for null
    }

    public List<TaskSummary> getAllTasks(ProcessInstance processInstance) {
        List<Status> all = new ArrayList<>(Arrays.asList(Status.values()));

        TaskService taskService = getTaskService();
        return taskService.getTasksByStatusByProcessInstanceId(processInstance.getId(), all, LANGUAGE);

    }

    public TaskStatus getTaskStatus(Integer productId, long taskId) throws HeuristicRollbackException, HeuristicMixedException, NotSupportedException, RollbackException, SystemException {
        TaskSummary taskSummary = getTaskById(productId, taskId);
        if (taskSummary != null) {
            return TaskStatus.fromStatus(taskSummary.getStatus());
        } else {
            return null;
        }
    }

    private TaskSummary getTaskById(Integer productId, long taskId) throws HeuristicRollbackException, HeuristicMixedException, NotSupportedException, RollbackException, SystemException {
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

    private void notifyTaskStatusUpdated(Integer productId, long taskId) throws HeuristicRollbackException, HeuristicMixedException, NotSupportedException, RollbackException, SystemException {
        TaskSummary taskById = getTaskById(productId, taskId);
        Status newStatus = taskById.getStatus();
        List<TaskStatusListener> taskListeners = statusUpdateListeners.stream().filter(l -> l.getTaskId() == taskId)
                .collect(Collectors.toList());
        taskListeners.forEach(l -> l.onStatusUpdate(TaskStatus.fromStatus(newStatus)));
    }

    public void startUserTask(UserTask userTask) throws HeuristicRollbackException, HeuristicMixedException, NotSupportedException, RollbackException, SystemException {
        Integer productId = userTask.getProductId();
        Integer taskId = userTask.getTaskId();
        TaskSummary task = getTaskById(productId, taskId);
        userTask.setTask(task);
        userTask.setTaskService(getTaskService());
        getTaskService().start(taskId, userTask.getUser().getUsername());
    }

    class TaskCompleteListener extends DefaultProcessEventListener {
        @Override
        public void afterNodeLeft(ProcessNodeLeftEvent event) {
            long taskId = event.getNodeInstance().getId();
            int productId = getProductId(event.getProcessInstance());
            try {
                notifyTaskStatusUpdated(productId, taskId);
            } catch (HeuristicRollbackException e) {
                //TODO
                e.printStackTrace();
            } catch (HeuristicMixedException e) {
                e.printStackTrace();
            } catch (NotSupportedException e) {
                e.printStackTrace();
            } catch (RollbackException e) {
                e.printStackTrace();
            } catch (SystemException e) {
                e.printStackTrace();
            }
        }
    }


}
