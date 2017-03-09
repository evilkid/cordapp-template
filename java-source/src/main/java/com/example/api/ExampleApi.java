package com.example.api;

import net.corda.core.contracts.*;
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
import java.util.Currency;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;

// This API is accessible from /api/example. All paths specified below are relative to it.
@Path("example")
public class ExampleApi {

    private final String myLegalName;

    private final String NOTARY_NAME = "Controller";

    private final CordaRPCOps services;

    private List<Party> notaries;
    private List<Party> peers;
    private List<Party> issuers;

    public ExampleApi(CordaRPCOps services) {
        this.myLegalName = services.nodeIdentity().getLegalIdentity().getName();
        this.services = services;
        updatePeers();
        updateIssuers();
        updateNotaries();
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
    @Path("pay/{peerName}/{amount}")
    public String pay(@PathParam("peerName") String peerName, @PathParam("amount") int quantity) {
        System.out.println("starting");

        Party party = services.partyFromName(peerName);

        if (party == null) {
            return "Peer not found";
        }
        try {
            System.out.println("Issuer: " + issuers.get(0));


            Amount<Issued<Currency>> amount = new Amount<>(quantity, new Issued<>(new PartyAndReference(issuers.get(0), OpaqueBytes.Companion.of((byte) 1)), ContractsDSL.USD));
            CashFlowCommand.PayCash cash = new CashFlowCommand.PayCash(amount, party);

            FlowHandle handle = cash.startFlow(services);

            //SignedTransaction a = new SignedTransaction();
            System.out.println(handle.getReturnValue().get().getClass());

            return handle.getReturnValue().get().toString();
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @GET
    @Path("vault")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<ContractState>> getPurchaseOrders() {
        return services.vaultAndUpdates().getFirst();
    }

    @GET
    @Path("issuers")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getIssuers() {
        updateIssuers();
        return issuers.stream().map(Party::getName).collect(toList());
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
                        .filter(name -> !name.equals(myLegalName) && !name.equals(NOTARY_NAME))
                        .collect(toList()));
    }

    @GET
    @Path("notaries ")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Party> getNotaryList() {
        /*new CashCommand.IssueCash(new Amount<Currency>(10, ContractsDSL.USD), OpaqueBytes.Companion.of(1), services.nodeIdentity().getLegalIdentity() );
        services.currentNodeTime();*/
        //return "test " + new NetworkIdentityModel().getNotaries().size();
        if (notaries.isEmpty()) {
            updateNotaries();
        }
        return notaries;
    }

    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> whoami() {
        return singletonMap("me", myLegalName);
    }

    private void updatePeers() {
        peers = new ArrayList<>();

        peers = services.networkMapUpdates().getFirst()
                .stream()
                .map(NodeInfo::getLegalIdentity)
                .filter(name -> !name.equals(myLegalName) && !name.equals(NOTARY_NAME))
                .collect(toList());
    }

    private void updateIssuers() {
        issuers = new ArrayList<>();
        for (NodeInfo nodeInfo :
                services.networkMapUpdates().getFirst()) {
            for (ServiceEntry serviceEntry :
                    nodeInfo.getAdvertisedServices()) {
                if (serviceEntry.getInfo().getType().getId().contains("corda.issuer.")) {
                    issuers.add(nodeInfo.getLegalIdentity());
                }
            }
        }
    }

    private void updateNotaries() {
        notaries = new ArrayList<>();

        for (NodeInfo nodeInfo :
                services.networkMapUpdates().getFirst()) {
            for (ServiceEntry serviceEntry :
                    nodeInfo.getAdvertisedServices()) {
                if (serviceEntry.getInfo().getType().isNotary()) {
                    notaries.add(nodeInfo.getNotaryIdentity());
                }
            }
        }
    }

}