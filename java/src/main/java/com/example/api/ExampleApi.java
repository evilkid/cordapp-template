package com.example.api;

import net.corda.core.contracts.*;
import net.corda.core.crypto.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowHandle;
import net.corda.core.node.NodeInfo;
import net.corda.core.node.ServiceEntry;
import net.corda.core.serialization.OpaqueBytes;
import net.corda.flows.CashCommand;
import net.corda.flows.CashFlow;
import net.corda.flows.IssuerFlow;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.*;

import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;

// This API is accessible from /api/example. All paths specified below are relative to it.
@Path("example")
public class ExampleApi {
    private final CordaRPCOps services;
    private final String myLegalName;

    private final String NOTARY_NAME = "Controller";

    private List<Party> notaries;
    private List<Party> peers;
    private List<Party> issuers;

    public ExampleApi(CordaRPCOps services) {
        this.services = services;
        this.myLegalName = services.nodeIdentity().getLegalIdentity().getName();
        updateNotaries();
        updatePeers();
        updateIssuers();
    }

    private Party findPeerByName(String peerName) {
        Optional<Party> party = services.networkMapUpdates().getFirst()
                .stream()
                .map(NodeInfo::getLegalIdentity)
                .filter(node -> node.getName().equals(peerName))
                .findFirst();


        return party.isPresent() ? party.get() : null;

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

    private void updatePeers() {
        peers = new ArrayList<>();

        peers = services.networkMapUpdates().getFirst()
                .stream()
                .map(NodeInfo::getLegalIdentity)
                .filter(node -> !node.getName().equals(myLegalName) && !node.getName().equals(NOTARY_NAME))
                .collect(toList());
    }

    @GET
    @Path("test")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Party> getNotaryList() {
        /*new CashCommand.IssueCash(new Amount<Currency>(10, ContractsDSL.USD), OpaqueBytes.Companion.of(1), services.nodeIdentity().getLegalIdentity() );
        services.currentNodeTime();*/
        //return "test " + new NetworkIdentityModel().getNotaries().size();
        return notaries;
    }

    @GET
    @Path("issue/{amount}")
    public String issue(@PathParam("amount") int quantity) {
        System.out.println("starting");
        try {
            CashCommand.IssueCash cash = new CashCommand.IssueCash(new Amount<>(quantity, ContractsDSL.USD), OpaqueBytes.Companion.of((byte) 1), peers.get(0), notaries.get(0));
            System.out.println("created");

            FlowHandle handle = services.startFlowDynamic(IssuerFlow.IssuanceRequester.class, cash.getAmount(), cash.getRecipient(), cash.getIssueRef(), services.nodeIdentity().getLegalIdentity());
            System.out.println("handeled");
            CashFlow.Companion.EXITING ex = (CashFlow.Companion.EXITING) handle.getReturnValue().get();
            System.out.println("exec");

            return "issued";
        } catch (Exception e) {
            return e.getMessage();
        }
    }


@GET
@Path("pay/{peerName}/{amount}")
public String pay(@PathParam("peerName") String peerName, @PathParam("amount") int quantity) {
    System.out.println("starting");

    Party party = findPeerByName(peerName);

    if (party == null) {
        return "Peer not found";
    }
    try {
        System.out.println("Issuer: " + issuers.get(0));


        Amount<Issued<Currency>> amount = new Amount<>(quantity, new Issued<>(new PartyAndReference(issuers.get(0), OpaqueBytes.Companion.of((byte) 1)), ContractsDSL.USD));
        CashCommand.PayCash cash = new CashCommand.PayCash(amount, party);
        FlowHandle handle = services.startFlowDynamic(CashFlow.class, cash);

        handle.getProgress().subscribe(o -> System.out.println("done " + o));

        System.out.println("result " + handle.getReturnValue().get());

        return "issued ";
    } catch (Exception e) {
        e.printStackTrace();
        return "err " + e.getMessage();

    }
}


    /**
     * Returns the party name of the node providing this end-point.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> whoami() {
        return singletonMap("me", myLegalName);
    }

    /**
     * Returns all parties registered with the [NetworkMapService]. The names can be used to look up identities by
     * using the [IdentityService].
     */
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
    @Path("issuers")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getIssuers() {
        updateIssuers();
        return issuers.stream().map(Party::getName).collect(toList());
    }

    /**
     * Displays all purchase order states that exist in the vault.
     */
    @GET
    @Path("purchase-orders")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<ContractState>> getPurchaseOrders() {
        return services.vaultAndUpdates().getFirst();
    }

    /**
     * This should only be called from the 'buyer' node. It initiates a flow to agree a purchase order with a
     * seller. Once the flow finishes it will have written the purchase order to ledger. Both the buyer and the
     * seller will be able to see it when calling /api/example/purchase-orders on their respective nodes.
     * <p>
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     * <p>
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    /*@PUT
    @Path("{party}/create-purchase-order")
    public Response createPurchaseOrder(PurchaseOrder purchaseOrder, @PathParam("party") String partyName) throws InterruptedException, ExecutionException {
        final Party otherParty = services.partyFromName(partyName);

        if (otherParty == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        final PurchaseOrderState state = new PurchaseOrderState(
                purchaseOrder,
                services.nodeIdentity().getLegalIdentity(),
                otherParty,
                new PurchaseOrderContract());

        // The line below blocks and waits for the flow to return.
        final ExampleFlow.ExampleFlowResult result = getOrThrow(services
                .startFlowDynamic(ExampleFlow.Initiator.class, state, otherParty)
                .getReturnValue(), null);

        final Response.Status status;
        if (result instanceof ExampleFlow.ExampleFlowResult.Success) {
            status = Response.Status.CREATED;
        } else {
            status = Response.Status.BAD_REQUEST;
        }

        return Response
                .status(status)
                .entity(result.toString())
                .build();
    }*/
}