package org.jboss.pnc.processes.test;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-01-29.
 */
//@RunWith(Arquillian.class)
public class BpmTest {

//    private static final Logger log = Logger.getLogger(BpmTest.class.getName());
//
//    public static final String PROCESS_ID = "org.jboss.pnc.defaultbuild2";
//    public static final String LANGUAGE = "en-UK";
//
//    @Deployment
//    public static WebArchive createDeployment() {
//
//        WebArchive war = ShrinkWrap.create(WebArchive.class)
//                .addPackage(RuntimeManagerProducer.class.getPackage())
//                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
//                .addAsManifestResource(EmptyAsset.INSTANCE, "kproject.xml")
//                .addAsResource("META-INF/test-persistence.xml")
//                .addAsResource("META-INF/Taskorm.xml")
//                .addAsResource("META-INF/TaskAuditorm.xml")
//                .addAsResource("META-INF/logging.properties")
//                //.addAsResource("org.jboss.pnc/default-build2.bpmn2")
//                .addAsResource("jndi.properties")
//                .addAsResource("jBPM.properties");
//
//        System.out.println(war.toString(true));
//        return war;
//    }
//
////    @Inject
////    ProductProcessManager productProcessManager;
//
////    @Inject
////    TaskService taskService;
//
////    @Test
////    public void listTasksTestCase() {
////        ProductBuildProcessInstance productBuildProcessInstance = productProcessManager.startBuild();
////        ProcessInstance processInstance = productBuildProcessInstance.getProcessInstance();
////        log.info("Process instance id: " + processInstance.getId());
////        log.info("Process id: " + processInstance.getProcessId());
////        log.info("Process name: " + processInstance.getProcessName());
////        log.info("Process state: " + processInstance.getState());
////
////        List<Long> tasksByProcessInstanceId = taskService.getTasksByProcessInstanceId(processInstance.getId());
////        tasksByProcessInstanceId.forEach((taskId) -> log.info("Task id: " + taskId));
////    }
//
//    @Inject
//    RuntimeManager runtimeManager;
//
//    @Test
//    public void sampleProcessTestCase() {
//
//        RuntimeEngine engine = runtimeManager.getRuntimeEngine(null);
//        KieSession kieSession = engine.getKieSession();
//        TaskService taskService = engine.getTaskService();
//
//        Collection<ProcessInstance> processInstances = kieSession.getProcessInstances();
//        Assert.assertEquals(processInstances.size(), 0);
//
//        Map<String, Object> params = new HashMap<>();
//        ProcessInstance processInstance = kieSession.startProcess(PROCESS_ID, params);
//
//        Collection<ProcessInstance> processInstancesAfterStart = kieSession.getProcessInstances();
//        Assert.assertEquals(processInstancesAfterStart.size(), 1);
//
//        ProcessInstance processInstance1 = processInstancesAfterStart.stream()
//                .filter(pi -> pi.getProcessId().equals(PROCESS_ID))
//                .collect(Collectors.toList()).get(0);
//        Assert.assertEquals("", processInstance, processInstance1);
//
//        List<Long> completedTasks = taskService.getTasksByProcessInstanceId(processInstance.getId());
//        System.out.println("Tasks in proc:" + completedTasks);
//
//        Assert.assertEquals(completedTasks.size(), 1);
//        Assert.assertEquals(taskService.getTaskById(completedTasks.get(completedTasks.size() - 1)).getName(), "Basic Config");
//
//        List<Status> filterReady = new ArrayList<>();
//        filterReady.add(Status.Ready);
//        List<TaskSummary> tasksReady = taskService
//                .getTasksByStatusByProcessInstanceId(processInstance.getId(), filterReady, LANGUAGE);
//
//        Assert.assertEquals(tasksReady.size(), 1);
//        TaskSummary humanTaskWaiting = tasksReady.get(0);
//
//        List<TaskSummary> list = taskService.getTasksAssignedAsPotentialOwner("john", LANGUAGE);
//        TaskSummary taskForJohn = list.get(0);
//
//        Assert.assertEquals("", humanTaskWaiting, taskForJohn);
//
//        // let john execute Task 1
//        System.out.println("John is executing task " + taskForJohn.getName());
//        taskService.start(taskForJohn.getId(), "john");
//        taskService.complete(taskForJohn.getId(), "john", null);
//
//        // let mary execute Task 2
//        list = taskService.getTasksAssignedAsPotentialOwner("mary", LANGUAGE);
//        taskForJohn = list.get(0);
//        System.out.println("Mary is executing task " + taskForJohn.getName());
//        taskService.start(taskForJohn.getId(), "mary");
//        taskService.complete(taskForJohn.getId(), "mary", null);
//
//        runtimeManager.disposeRuntimeEngine(engine);
//    }




}
