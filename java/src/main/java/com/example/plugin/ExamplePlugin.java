package com.example.plugin;

import com.esotericsoftware.kryo.Kryo;
import com.example.api.ExampleApi;
import net.corda.core.ErrorOr;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.PartyAndReference;
import net.corda.core.crypto.Party;
import net.corda.core.flows.IllegalFlowLogicException;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowHandle;
import net.corda.core.node.CordaPluginRegistry;
import net.corda.core.node.PluginServiceHub;
import net.corda.core.serialization.OpaqueBytes;
import net.corda.flows.CashCommand;
import net.corda.flows.CashFlow;
import net.corda.flows.IssuerFlow;
import rx.Notification;

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
     *
     * Name of the flow to be invoked -> Set of the parameter types passed into the flow.
     *
     * E.g. In the case of this CorDapp:
     *
     * "ExampleFlow.Initiator" -> Set(PurchaseOrderState, Party)
     *
     * This map also acts as a white list. If a flow is invoked via the API and not registered correctly
     * here, then the flow state machine will _not_ invoke the flow. Instead, an exception will be raised.
     */
    private final Map<String, Set<String>> requiredFlows = Collections.singletonMap(
            IssuerFlow.IssuanceRequester.class.getName(),
            new HashSet<>(Arrays.asList(
                    CashFlow.class.getName(),
                    Party.class.getName(),
                    Amount.class.getName(),
                    OpaqueBytes.class.getName()
            )));

    /**
     * A list of long lived services to be hosted within the node. Typically you would use these to register flow
     * factories that would be used when an initiating party attempts to communicate with our node using a particular
     * flow. See the [ExampleService.Service] class for an implementation.
     */
    private final List<Function<PluginServiceHub, ?>> servicePlugins = Collections.singletonList(IssuerFlow.Issuer.Service::new);

    /**
     * A list of directories in the resources directory that will be served by Jetty under /web.
     */
    private final Map<String, String> staticServeDirs = Collections.singletonMap(
            // This will serve the exampleWeb directory in resources to /web/example
            "example", getClass().getClassLoader().getResource("exampleWeb").toExternalForm()
    );

    @Override public List<Function<CordaRPCOps, ?>> getWebApis() { return webApis; }
    @Override public Map<String, Set<String>> getRequiredFlows() { return requiredFlows; }
    @Override public List<Function<PluginServiceHub, ?>> getServicePlugins() { return servicePlugins; }
    @Override public Map<String, String> getStaticServeDirs() { return staticServeDirs; }

    /**
     * Register required types with Kryo (our serialisation framework).
     */
    @Override public boolean registerRPCKryoTypes(Kryo kryo) {
        kryo.register(Date.class,5);
        kryo.register(IllegalArgumentException.class,9);
        kryo.register(IllegalFlowLogicException.class,10);

        /*CashFlow.class.getName(),
                    Party.class.getName(),
                    Amount.class.getName(),
                    OpaqueBytes.class.getName()*/

        kryo.register(CashFlow.class,11);
        kryo.register(CashFlow.Companion.EXITING.class,12);
        kryo.register(CashFlow.Companion.ISSUING.class,13);
        kryo.register(CashFlow.Companion.PAYING.class,14);

        kryo.register(Party.class,15);
        kryo.register(Amount.class,16);
        kryo.register(Amount.class,17);
        kryo.register(OpaqueBytes.class,18);

        kryo.register(IssuerFlow.class,19);
        kryo.register(IssuerFlow.IssuanceRequester.class,20);
        kryo.register(IssuerFlow.IssuanceRequestState.class,21);
        kryo.register(IssuerFlow.Issuer.Service.class,22);

        kryo.register(IssuerFlow.Issuer.Companion.AWAITING_REQUEST.class,23);
        kryo.register(IssuerFlow.Issuer.Companion.ISSUING.class,24);
        kryo.register(IssuerFlow.Issuer.Companion.SENDING_CONFIRM.class,25);
        kryo.register(IssuerFlow.Issuer.Companion.TRANSFERRING.class,26);


        kryo.register(CashCommand.PayCash.class,29);
        kryo.register(PartyAndReference.class,30);
        kryo.register(Notification.class,31);


        kryo.register(FlowHandle.class,27);
        kryo.register(ErrorOr.class,28);
        return true;
    }
}