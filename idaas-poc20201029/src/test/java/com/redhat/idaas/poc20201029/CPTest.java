package com.redhat.idaas.poc20201029;

import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;

public class CPTest {

    @Test
    public void test1() {
        KieServices ks = KieServices.Factory.get();
        KieContainer kieContainer = ks.getKieClasspathContainer();

        System.out.println(kieContainer.getKieBaseNames());
    }
    
}
