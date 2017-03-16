package com.example.api;

import net.corda.core.contracts.*;
import net.corda.core.crypto.Party;
import net.corda.core.crypto.SecureHash;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowHandle;
import net.corda.core.node.NodeInfo;
import net.corda.core.node.ServiceEntry;
import net.corda.core.serialization.OpaqueBytes;
import net.corda.core.transactions.SignedTransaction;
import net.corda.flows.CashFlowCommand;
import net.corda.flows.IssuerFlow;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

            if (notaries.isEmpty()) {
                updateNotaries();
            }

            Party party = services.partyFromName(peerName);


            CashFlowCommand.IssueCash cash = new CashFlowCommand.IssueCash(new Amount<>((long) quantity, ContractsDSL.USD), OpaqueBytes.Companion.of((byte) 1), party, notaries.get(0));
            System.out.println("created");

            FlowHandle handle = services.startFlowDynamic(IssuerFlow.IssuanceRequester.class, cash.getAmount(), cash.getRecipient(), cash.getIssueRef(), services.nodeIdentity().getLegalIdentity());
            System.out.println("handeled");
            SignedTransaction signedTransaction = (SignedTransaction) handle.getReturnValue().get(10 * 1000, TimeUnit.MILLISECONDS);
            System.out.println("exec");

            return signedTransaction.getId().toString();
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

            FlowHandle<SignedTransaction> handle = cash.startFlow(services);

            //SignedTransaction a = new SignedTransaction();
            System.out.println(handle.getReturnValue().get().getClass());

            return handle.getReturnValue().get(10 * 1000, TimeUnit.MILLISECONDS).toString();
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @GET
    @Path("exit/{amount}")
    public String exit(@PathParam("amount") int quantity) {
        try {


            Amount<Currency> amount = new Amount<>((long) quantity, ContractsDSL.USD);

            System.out.println(amount);

            if (issuers.isEmpty()) {
                updateIssuers();
            }

            CashFlowCommand.ExitCash exitCash = new CashFlowCommand.ExitCash(amount, issuers.get(0).ref(OpaqueBytes.Companion.of((byte) 1)).getReference());

            FlowHandle<SignedTransaction> handle = exitCash.startFlow(services);


            return handle.getReturnValue().get(10 * 1000, TimeUnit.MILLISECONDS).toString();
        } catch (Exception e) {

            return e.getMessage();
        }

    }

    @GET
    @Path("vault")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<ContractState>> getAllTransactions() {
        return services.vaultAndUpdates().getFirst();
    }


    @GET
    @Path("vault/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public StateAndRef<ContractState> getTransactionById(@PathParam("id") String id) {

        for (StateAndRef stateAndRef : services.vaultAndUpdates().getFirst()) {

            if (stateAndRef.getRef().getTxhash().equals(SecureHash.parse(id))) {
                return stateAndRef;
            }
        }

        throw new NotFoundException("Could not find transaction");
    }

    @GET
    @Path("balance")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<Currency, Amount<Currency>> getBalance() {
        return services.getCashBalances();
    }

    @GET
    @Path("issuers")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getIssuers() {
        updateIssuers();
        return issuers.stream().map(Party::getName).collect(toList());
    }


    @GET
    @Path("issuers/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Party getIssuerByName(@PathParam("name") String name) {
        updateIssuers();
        return issuers.stream()
                .filter(party -> party.getName().equals(name))
                .findFirst()
                .orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getPeers() {
        updatePeers();
        return services.networkMapUpdates().getFirst()
                .stream()
                .map(node -> node.getLegalIdentity().getName())
                .filter(name -> !name.equals(myLegalName) && !name.equals(NOTARY_NAME))
                .collect(toList());
    }

    @GET
    @Path("peers/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Party getPeerByName(@PathParam("name") String name) {
        updateIssuers();
        return peers.stream()
                .filter(party -> party.getName().equals(name))
                .findFirst()
                .orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("notaries")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Party> getNotaryList() {
        if (notaries.isEmpty()) {
            updateNotaries();
        }
        return notaries;
    }

    @GET
    @Path("peers/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Party getNotariesByName(@PathParam("name") String name) {
        updateIssuers();
        return notaries.stream()
                .filter(party -> party.getName().equals(name))
                .findFirst()
                .orElseThrow(NotFoundException::new);
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