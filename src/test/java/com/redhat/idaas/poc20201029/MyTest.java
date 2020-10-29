package com.redhat.idaas.poc20201029;

import java.util.List;

import com.redhat.idaas.eventbuilder.events.platform.RoutingEvent;

import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ClaimCheckOperation;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.drools.compiler.kie.builder.impl.DrlProject;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.io.KieResources;
import org.kie.camel.embedded.dmn.DecisionsToHeadersProcessor;
import org.kie.camel.embedded.dmn.ToDMNEvaluateAllCommandProcessor;
import org.kie.camel.embedded.dmn.ToMapProcessor;

import javax.naming.Context;
import javax.naming.NamingException;

import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ClaimCheckOperation;
import org.drools.compiler.kie.builder.impl.DrlProject;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.io.KieResources;
import org.kie.api.runtime.KieSession;
import org.kie.camel.embedded.dmn.DecisionsToHeadersProcessor;
import org.kie.camel.embedded.dmn.ToDMNEvaluateAllCommandProcessor;
import org.kie.camel.embedded.dmn.ToMapProcessor;

import static com.redhat.idaas.poc20201029.TestUtils.*;

public class MyTest extends CamelTestSupport {
    protected Context jndiContext;

    @Override
    protected Context createJndiContext() throws Exception {
        jndiContext = super.createJndiContext();
        configureDroolsContext(jndiContext);
        return jndiContext;
    }

    protected void configureDroolsContext(Context jndiContext) {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        KieResources kieResources = ks.getResources();

        kfs.write("src/main/resources/RoutingEvent.dmn", kieResources.newClassPathResource("/RoutingEvent.dmn", this.getClass()));

        KieBuilder kieBuilder = ks.newKieBuilder(kfs).buildAll(DrlProject.class);

        List<Message> errors = kieBuilder.getResults().getMessages(Message.Level.ERROR);
        if (!errors.isEmpty()) {
            fail("" + errors);
        }

        KieSession ksession = ks.newKieContainer(ks.getRepository().getDefaultReleaseId()).newKieSession();

        try {
            jndiContext.bind("ksession1", ksession);
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        final Processor toMap = new ToMapProcessor("event");
        final Processor toDMNCommand = new ToDMNEvaluateAllCommandProcessor("ns1", "RoutingEvent", "dmnResult");
        final Processor dmnToHeader = DecisionsToHeadersProcessor.builder("dmnResult", "topicsHeader", "topic names")
                                                                 .build();
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").claimCheck(ClaimCheckOperation.Push)
                                    .process(toMap)
                                    .process(toDMNCommand)
                                    .to("kie-local://ksession1?channel=default") // Drools+DMN processing of iDAAS object
                                    .process(dmnToHeader)
                                    .claimCheck(ClaimCheckOperation.Pop)
                                    .to("log:com.redhat.idaas?level=INFO&showAll=true&multiline=true")
                                    .choice()
                                    .when(simple("${header.topicsHeader} contains 'MMSAllADT'"))
                                        .to("mock:MMSAllADT")
                                    .when(simple("${header.topicsHeader} contains 'MMSDischarges'"))
                                        .to("mock:MMSDischarges")
                                    .otherwise()
                                        .to("mock:undefined");
            }
        };
    }
    @Test
    public void test() throws Exception {
        RoutingEvent adt1 = routingEvent("MMS", "ADT", null);
        RoutingEvent adt2 = routingEvent("MMS", "ADT", null);
        RoutingEvent discharge1 = routingEvent("MMS", "ADT", "A03");
        RoutingEvent mms = routingEvent("MMS", null, null);

        MockEndpoint mockMMSAllADT = getMockEndpoint("mock:MMSAllADT");
        mockMMSAllADT.expectedMessageCount(3);
        mockMMSAllADT.expectedBodiesReceived(adt1, adt2, discharge1);
        MockEndpoint mockMMSDischarges = getMockEndpoint("mock:MMSDischarges");
        mockMMSDischarges.expectedMessageCount(1);
        mockMMSDischarges.expectedBodiesReceived(discharge1);
        MockEndpoint mockundefined = getMockEndpoint("mock:undefined");
        mockundefined.expectedMessageCount(1);
        mockundefined.expectedBodiesReceived(mms);

        template.requestBody("direct:start", adt1);
        template.requestBody("direct:start", adt2);
        template.requestBody("direct:start", discharge1);
        template.requestBody("direct:start", mms);

        mockMMSAllADT.assertIsSatisfied();
    }
}