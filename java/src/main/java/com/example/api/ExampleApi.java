package com.example.api;

import net.corda.core.contracts.Amount;
import net.corda.core.contracts.ContractsDSL;
import net.corda.core.crypto.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowHandle;
import net.corda.core.node.NodeInfo;
import net.corda.core.node.ServiceEntry;
import net.corda.core.serialization.OpaqueBytes;
import net.corda.flows.CashFlowCommand;
import net.corda.flows.IssuerFlow;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static java.util.Collections.singletonMap;

// This API is accessible from /api/example. All paths specified below are relative to it.
@Path("example")
public class ExampleApi {

    private final CordaRPCOps services;

    private List<Party> notaries;
    private List<Party> peers;

    public ExampleApi(CordaRPCOps services) {
        this.services = services;
        updatePeers();
    }

    @GET
    @Path("issue/{peerName}/{amount}")
    public String issue(@PathParam("peerName") String peerName, @PathParam("amount") int quantity) {
        System.out.println("starting");
        try {

            Party party = services.partyFromName(peerName);

            CashFlowCommand.IssueCash cash = new CashFlowCommand.IssueCash(new Amount<>(quantity, ContractsDSL.USD), OpaqueBytes.Companion.of((byte) 1), party, notaries.get(0));
            System.out.println("created");

            FlowHandle handle = services.startFlowDynamic(IssuerFlow.IssuanceRequester.class, cash.getAmount(), cash.getRecipient(), cash.getIssueRef(), services.nodeIdentity().getLegalIdentity());
            System.out.println("handeled");
            handle.getReturnValue().get();
            System.out.println("exec");

            return "issued";
        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }


    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, List<String>> getPeers() {
        return singletonMap(
                "peers",
                services.networkMapUpdates().getFirst()
                        .stream()
                        .map(node -> node.getLegalIdentity().getName())
                        .collect(toList()));
    }


    private void updatePeers() {
        peers = new ArrayList<>();

        peers = services.networkMapUpdates().getFirst()
                .stream()
                .map(NodeInfo::getLegalIdentity)
                .collect(toList());
    }


}