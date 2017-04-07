package com.example.service;

import com.example.flow.ExampleFlow;
import kotlin.jvm.JvmClassMappingKt;
import net.corda.core.node.PluginServiceHub;

/**
 * Created by evilkid on 4/7/2017.
 */
public class ExampleService {

    public ExampleService(PluginServiceHub services) {
        System.out.println("Registering...");
        services.registerFlowInitiator(JvmClassMappingKt.getKotlinClass(ExampleFlow.MasterFxFlow.class), ExampleFlow.CurrencyResponder::new);
        services.registerFlowInitiator(JvmClassMappingKt.getKotlinClass(ExampleFlow.ExchangeInitiator.class), ExampleFlow.ExchangeResponder::new);
    }
}
