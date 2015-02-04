package org.jboss.pnc.processes;

import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeEnvironment;
import org.kie.api.runtime.manager.RuntimeEnvironmentBuilder;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.manager.RuntimeManagerFactory;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.runtime.manager.context.EmptyContext;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-01-16.
 */
public class ProjectBuilder {


    private KieSession kieSession;

    public ProjectBuilder() {
        // first configure environment that will be used by RuntimeManager
        RuntimeEnvironment environment = RuntimeEnvironmentBuilder.Factory.get()
                .newDefaultInMemoryBuilder()
                .addAsset(ResourceFactory.newClassPathResource("BPMN2-ScriptTask.bpmn2"), ResourceType.BPMN2)
                .get();

        // next create RuntimeManager - in this case singleton strategy is chosen
        RuntimeManager manager = RuntimeManagerFactory.Factory.get().newSingletonRuntimeManager(environment);

        // then get RuntimeEngine out of manager - using empty context as singleton does not keep track
        // of runtime engine as there is only one
        RuntimeEngine runtime = manager.getRuntimeEngine(EmptyContext.get());

        // get KieSession from runtime runtimeEngine - already initialized with all handlers, listeners, etc that were configured
        // on the environment
        kieSession = runtime.getKieSession();

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
/*
    ProjectBuildInstance startBuild(ProjectBuildConfiguration buildConfiguration) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", buildConfiguration.getId());
        ProcessInstance processInstance = kieSession.startProcess("org.jboss.pnc.process.projectBuild", params);

        return new ProjectBuildInstance(processInstance);
    } */
}
