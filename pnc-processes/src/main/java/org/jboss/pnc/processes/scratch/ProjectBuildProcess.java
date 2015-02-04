package org.jboss.pnc.processes.scratch;

import org.jboss.pnc.spi.builddriver.BuildDriver;
import org.jbpm.bpmn2.xml.XmlBPMNProcessDumper;
import org.jbpm.process.instance.impl.Action;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.kie.api.io.ResourceType;
import org.kie.internal.KnowledgeBase;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.runtime.StatefulKnowledgeSession;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-01-16.
 */
public class ProjectBuildProcess {

    BuildDriver buildDriver;

    public ProjectBuildProcess() {

        RuleFlowProcessFactory factory =
                RuleFlowProcessFactory.createProcess("org.jbpm.HelloWorld");
        Action buildProject1 = (processContext) -> {
//            ProjectBuildConfiguration buildConfiguration = (ProjectBuildConfiguration) processContext.getNodeInstance().getVariable("buildConfiguration");
//            RepositoryConfiguration repositoryConfiguration = (RepositoryConfiguration) processContext.getNodeInstance().getVariable("repositoryConfiguration");
//            buildDriver.startProjectBuild(buildConfiguration, repositoryConfiguration);
        };

        factory
                // Header
                .name("HelloWorldProcess")
                .version("1.0")
                .packageName("org.jbpm")
                        // Nodes
                .startNode(1).name("Start").done()
                .actionNode(2).name("Action")
                //.action("java", "System.out.println(\"Hello World\");").done()
                .action(buildProject1).done()
                .endNode(3).name("End").done()
                // Connections
                .connection(1, 2)
                .connection(2, 3);
        RuleFlowProcess process = factory.validate().getProcess();
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(ResourceFactory.newByteArrayResource(
                XmlBPMNProcessDumper.INSTANCE.dump(process).getBytes()), ResourceType.BPMN2);
        KnowledgeBase kbase = kbuilder.newKnowledgeBase();
        StatefulKnowledgeSession ksession = kbase.newStatefulKnowledgeSession();

        ksession.startProcess("org.jbpm.HelloWorld");
    }
}
