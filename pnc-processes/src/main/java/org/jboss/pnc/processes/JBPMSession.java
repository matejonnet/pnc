package org.jboss.pnc.processes;

import javax.enterprise.context.ApplicationScoped;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-01-28.
 */
@ApplicationScoped
public class JBPMSession {
/*
    Logger log = Logger.getLogger(JBPMSession.class.getName());

    private final KieSession kieSession;

//    @Inject
//    private RuntimeManager runtimeManager;
//    private final RuntimeEngine runtimeEngine;

    public JBPMSession() {
        //TODO
        // and last dispose the runtime engine
        //manager.disposeRuntimeEngine(runtime);

        KieHelper kieHelper = new KieHelper();
        KieBase kieBase = kieHelper
                .addResource(ResourceFactory.newClassPathResource("org.jboss.pnc/default-build.bpmn"))
                .build();
        kieSession = kieBase.newKieSession();

        WorkItemHandler humanTaskHandler = new WorkItemHandler() {
            @Override
            public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
                log.info("Human starting task id: " + workItem.getId() + " name: " + workItem.getName() + " ...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
               // manager.completeWorkItem(workItem.getId(), new HashMap<>());
               // log.info("Human task id:" + workItem.getId() + " done.");
            }

            @Override
            public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {

            }
        };


        kieSession.getWorkItemManager().registerWorkItemHandler("Human Task", humanTaskHandler);
//        log.info("runtimeManager.getIdentifier(): " + runtimeManager.getIdentifier());

    }



    public JBPMSession() {
        RuntimeEnvironmentBuilder runtimeEnvironmentBuilder = RuntimeEnvironmentBuilder.Factory.get()
                .newDefaultInMemoryBuilder();


//        RuntimeEnvironmentBuilder runtimeEnvironmentBuilder = new org.jbpm.runtime.manager.impl.RuntimeEnvironmentBuilder();
//        runtimeEnvironmentBuilder
//                .addConfiguration("drools.processSignalManagerFactory", DefaultSignalManagerFactory.class.getName())
//                .addConfiguration("drools.processInstanceManagerFactory", DefaultProcessInstanceManagerFactory.class.getName());

        RuntimeEnvironment environment = runtimeEnvironmentBuilder
                .addAsset(ResourceFactory.newClassPathResource("org.jboss.pnc/default-build.bpmn"), ResourceType.BPMN2)
                .get();

        // next create RuntimeManager - in this case singleton strategy is chosen
        manager = RuntimeManagerFactory.Factory.get().newSingletonRuntimeManager(environment);

        // then get RuntimeEngine out of manager - using empty context as singleton does not keep track
        // of runtime engine as there is only one
        runtimeEngine = manager.getRuntimeEngine(EmptyContext.get());

        // get KieSession from runtime runtimeEngine - already initialized with all handlers, listeners, etc that were configured
        // on the environment
        kieSession = runtimeEngine.getKieSession();

        //TODO
        // and last dispose the runtime engine
        //manager.disposeRuntimeEngine(runtime);

//        InputStream inputStreamResource = getClass().getClassLoader().getResourceAsStream("ProjectBuilder.bpmn");
//        KieHelper kieHelper = new KieHelper();
//        KieBase kieBase = kieHelper
//                .addResource(ResourceFactory.newClassPathResource("ProjectBuilder.bpmn"))
//                //.addResource(new InputStreamResource(inputStreamResource))
//                .build();
        //kieSession = kieBase.newKieSession();

    }

    @Produces
    public KieSession getKieSession() {
        return kieSession;
    }

//    @Produces
//    public RuntimeManager getRuntimeManager() {
//        return manager;
//    }
//
//    @Produces
//    public TaskService getTaskService() {
//        return runtimeEngine.getTaskService();
//    }
    */
}
