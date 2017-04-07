package com.example.plugin;

import com.example.api.ExampleApi;
import com.example.flow.ExampleFlow;
import com.example.service.ExampleService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.corda.core.contracts.Amount;
import net.corda.core.crypto.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.CordaPluginRegistry;
import net.corda.core.node.PluginServiceHub;
import net.corda.core.serialization.OpaqueBytes;
import net.corda.core.serialization.SerializationCustomization;
import net.corda.flows.AbstractCashFlow;
import net.corda.flows.IssuerFlow;
import net.corda.node.services.statemachine.FlowSessionException;

import java.util.*;
import java.util.function.Function;

public class ExamplePlugin extends CordaPluginRegistry {
    /**
     * A list of classes that expose web APIs.
     */
    private final List<Function<CordaRPCOps, ?>> webApis = Collections.singletonList(ExampleApi::new);

    /**
     * A list of flows required for this CorDapp. Any flow which is invoked from from the web API needs to be
     * registered as an entry into this map. The map takes the form:
     * <p>
     * Name of the flow to be invoked -> Set of the parameter types passed into the flow.
     * <p>
     * E.g. In the case of this CorDapp:
     * <p>
     * "ExampleFlow.Initiator" -> Set(PurchaseOrderState, Party)
     * <p>
     * This map also acts as a white list. If a flow is invoked via the API and not registered correctly
     * here, then the flow state machine will _not_ invoke the flow. Instead, an exception will be raised.
     */
    private final Map<String, Set<String>> requiredFlows = ImmutableMap.of(
            IssuerFlow.IssuanceRequester.class.getName(),
            new HashSet<>(Arrays.asList(
                    AbstractCashFlow.class.getName(),
                    Party.class.getName(),
                    Amount.class.getName(),
                    OpaqueBytes.class.getName()
            )),
            ExampleFlow.MasterFxFlow.class.getName(),
            new HashSet<>(Arrays.asList(
                    Party.class.getName(),
                    Party.class.getName(),
                    Amount.class.getName())
            )
    );

    /**
     * A list of long lived services to be hosted within the node. Typically you would use these to register flow
     * factories that would be used when an initiating party attempts to communicate with our node using a particular
     * flow. See the [ExampleService.Service] class for an implementation.
     */

    private final List<Function<PluginServiceHub, ?>> servicePlugins = ImmutableList.of(IssuerFlow.Issuer.Service::new, ExampleService::new);

    /**
     * A list of directories in the resources directory that will be served by Jetty under /web.
     */
    private final Map<String, String> staticServeDirs = Collections.singletonMap(
            // This will serve the exampleWeb directory in resources to /web/example
            "example", getClass().getClassLoader().getResource("templateWeb").toExternalForm()
    );

    @Override
    public List<Function<CordaRPCOps, ?>> getWebApis() {
        return webApis;
    }

    @Override
    public Map<String, Set<String>> getRequiredFlows() {
        return requiredFlows;
    }

    @Override
    public List<Function<PluginServiceHub, ?>> getServicePlugins() {
        return servicePlugins;
    }

    @Override
    public Map<String, String> getStaticServeDirs() {
        return staticServeDirs;
    }

    /**
     * Register required types with Kryo (our serialisation framework).
     */

    @Override
    public boolean customizeSerialization(SerializationCustomization custom) {
        custom.addToWhitelist(FlowSessionException.class);
        custom.addToWhitelist(List.class);
        custom.addToWhitelist(ArrayList.class);


        //java.util.LinkedHashMap$LinkedKeySet
        return true;
    }
}