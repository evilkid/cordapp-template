package com.example.api;

import com.example.flow.ExampleFlow;
import com.example.models.CurrencyRate;
import com.example.models.PeerInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import net.corda.core.contracts.*;
import net.corda.core.crypto.CompositeKey;
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
import net.corda.jackson.JacksonSupport;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    public static <T> T getLastElement(final Iterable<T> elements) {
        final Iterator<T> itr = elements.iterator();
        T lastElement = itr.next();

        while (itr.hasNext()) {
            lastElement = itr.next();
        }

        return lastElement;
    }

    @GET
    @Path("issue/{peerName}/{amount}/{currency}")
    public String issueCurrency(@PathParam("peerName") String peerName, @PathParam("amount") int quantity, @PathParam("currency") String currency) {
        try {
            return issueMoney(peerName, quantity, ContractsDSL.currency(currency));
        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    @GET
    @Path("issue/{peerName}/{amount}")
    public String issue(@PathParam("peerName") String peerName, @PathParam("amount") int quantity) {
        try {
            return issueMoney(peerName, quantity, ContractsDSL.USD);
        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    private String issueMoney(String peerName, long quantity, Currency currency) throws Exception {
        if (notaries.isEmpty()) {
            updateNotaries();
        }

        Party party = services.partyFromName(peerName);


        CashFlowCommand.IssueCash cash = new CashFlowCommand.IssueCash(new Amount<>(quantity, currency), OpaqueBytes.Companion.of((byte) 1), party, notaries.get(0));

        FlowHandle handle = services.startFlowDynamic(IssuerFlow.IssuanceRequester.class, cash.getAmount(), cash.getRecipient(), cash.getIssueRef(), services.nodeIdentity().getLegalIdentity());
        SignedTransaction signedTransaction = (SignedTransaction) handle.getReturnValue().get(10 * 1000, TimeUnit.MILLISECONDS);

        return signedTransaction.getId().toString();
    }

    @GET
    @Path("pay/{peerName}/{amount}/{currency}")
    public String pay(@PathParam("peerName") String peerName, @PathParam("amount") int quantity, @PathParam("currency") String currency) {
        System.out.println("starting");

        Party party = services.partyFromName(peerName);

        if (party == null) {
            return "PeerInfo not found";
        }

        try {
            Amount<Issued<Currency>> amount = new Amount<>(
                    quantity,
                    new Issued<>(new PartyAndReference(issuers.get(0), OpaqueBytes.Companion.of((byte) 1)),
                            ContractsDSL.currency(currency)
                    )
            );

            CashFlowCommand.PayCash cash = new CashFlowCommand.PayCash(amount, party);

            FlowHandle<SignedTransaction> handle = cash.startFlow(services);

            SignedTransaction tx = handle.getReturnValue().get(10 * 1000, TimeUnit.MILLISECONDS);

            return tx.getId().toString();
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @GET
    @Path("exchange/{recipient}/{quantity}/{currency}")
    @Produces(MediaType.APPLICATION_JSON)
    public String exchange(@PathParam("quantity") int quantity, @PathParam("recipient") String recipient, @PathParam("currency") String currency) {

        Amount<Issued<Currency>> amount = new Amount<>(
                quantity,
                new Issued<>(new PartyAndReference(issuers.get(0), OpaqueBytes.Companion.of((byte) 1)),
                        ContractsDSL.currency(currency)
                )
        );

        FlowHandle flowHandle = services.startFlowDynamic(
                ExampleFlow.MasterFxFlow.class,
                services.partyFromName(recipient),
                services.partyFromName("NodeC"),
                amount);

        try {
            return ((SignedTransaction) flowHandle.getReturnValue().get(10 * 10000, TimeUnit.MILLISECONDS)).getId().toString();
        } catch (Exception e) {
            System.out.println("error1: " + e.getMessage());
        }

        return "done";
    }

    @GET
    @Path("exit/{amount}/{currency}")
    public String exit(@PathParam("amount") int quantity, @PathParam("currency") String currency) {
        try {
            Amount<Currency> amount = new Amount<>((long) quantity, ContractsDSL.currency(currency));

            System.out.println(amount);

            if (issuers.isEmpty()) {
                updateIssuers();
            }

            CashFlowCommand.ExitCash exitCash = new CashFlowCommand.ExitCash(amount, issuers.get(0).ref(OpaqueBytes.Companion.of((byte) 1)).getReference());

            FlowHandle<SignedTransaction> handle = exitCash.startFlow(services);

            SignedTransaction tx = handle.getReturnValue().get(10 * 1000, TimeUnit.MILLISECONDS);

            return tx.getId().toString();
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
    public List<PeerInfo> getPeers() {
        updatePeers();

        return services
                .networkMapUpdates()
                .getFirst()
                .stream()
                .filter(peer -> !peer.getLegalIdentity().getName().equals(myLegalName)
                        && !peer.getLegalIdentity().getName().equals(NOTARY_NAME))
                .map(nodeInfo -> new PeerInfo(nodeInfo.getLegalIdentity().getName(),
                                nodeInfo.getAddress(),
                                nodeInfo.getPhysicalLocation(),
                                nodeInfo.getAdvertisedServices()
                        )
                )
                .collect(Collectors.toList());
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
    @Path("/peers/hash/{hash}")
    @Produces(MediaType.APPLICATION_JSON)
    public Party peerByHash(@PathParam("hash") String hash) {
        return services.partyFromKey(CompositeKey.Companion.parseFromBase58(hash));
    }

    @GET
    @Path("/traders")
    @Produces(MediaType.APPLICATION_JSON)
    public List<PeerInfo> getTraders() {
        return services
                .networkMapUpdates()
                .getFirst()
                .stream()
                .filter(peer -> !peer.getLegalIdentity().getName().equals(NOTARY_NAME)
                        && isTrader(peer)
                )
                .map(nodeInfo -> new PeerInfo(nodeInfo.getLegalIdentity().getName(),
                                nodeInfo.getAddress(),
                                nodeInfo.getPhysicalLocation(),
                                nodeInfo.getAdvertisedServices()
                        )
                )
                .collect(Collectors.toList());
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
    @Path("notaries/{name}")
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

    @GET
    @Path("rates/{from}/{to}/{rate}")
    @Produces(MediaType.APPLICATION_JSON)
    public Set<CurrencyRate> addRates(@PathParam("from") String from, @PathParam("to") String to, @PathParam("rate") float rate) {

        if (!isTrader()) {
            throw new NotAllowedException("Not a trader");
        }

        ObjectMapper json = JacksonSupport.createNonRpcMapper();
        Set<CurrencyRate> rates = getRates();


        //replace
        if (!rates.add(new CurrencyRate(from, to, rate))) {
            rates.remove(new CurrencyRate(from, to, rate));
            rates.add(new CurrencyRate(from, to, rate));
        }

        try {
            services.addVaultTransactionNote(SecureHash.sha256("rates"), json.writeValueAsString(rates));
            return rates;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @GET
    @Path("rates")
    @Produces(MediaType.APPLICATION_JSON)
    public Set<CurrencyRate> getRates() {
        if (!isTrader()) {
            throw new NotAllowedException("Not a trader");
        }

        ObjectMapper json = JacksonSupport.createNonRpcMapper();

        try {
            Iterable<String> ratesIterator = services.getVaultTransactionNotes(SecureHash.sha256("rates"));
            String rat = getLastElement(ratesIterator);

            System.out.println(rat);

            if (rat != null && !rat.isEmpty()) {
                return json.readValue(rat, new TypeReference<Set<CurrencyRate>>() {
                });

            }
        } catch (Exception e) {
            return new HashSet<>();
        }
        return new HashSet<>();
    }

    @GET
    @Path("/identity")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getLegalIdentity() {
        return ImmutableMap.of(
                "name", services.nodeIdentity().getLegalIdentity().getName(),
                "token", services.nodeIdentity().getLegalIdentity().getOwningKey(),
                "PhysicalLocation", services.nodeIdentity().getPhysicalLocation(),
                "advertisedServices", services.nodeIdentity().getAdvertisedServices(),
                "address", services.nodeIdentity().getAddress()
        );
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

    private boolean isTrader() {
        return isTrader(services.nodeIdentity());
    }

    private boolean isTrader(NodeInfo nodeInfo) {
        for (ServiceEntry serviceEntry : nodeInfo.getAdvertisedServices()) {
            if (serviceEntry.getInfo().component1().getId().equals("tn.fxtrader")) {
                return true;
            }
        }

        return false;
    }
}