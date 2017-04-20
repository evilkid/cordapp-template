package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.example.api.ExampleApi;
import com.example.models.CurrencyRate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.ContractsDSL;
import net.corda.core.contracts.Issued;
import net.corda.core.contracts.PartyAndReference;
import net.corda.core.crypto.Party;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.node.ServiceEntry;
import net.corda.core.serialization.CordaSerializable;
import net.corda.core.serialization.OpaqueBytes;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.UntrustworthyData;
import net.corda.flows.CashPaymentFlow;
import net.corda.jackson.JacksonSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Set;

/**
 * Created by evilkid on 4/6/2017.
 */
public class ExampleFlow {

    public static class MasterFxFlow extends FlowLogic<SignedTransaction> {

        private final Party fxTrader;
        private final Party receiver;
        private final Amount<Issued<Currency>> amount;


        public MasterFxFlow(Party receiver, Party fxTrader, Amount<Issued<Currency>> amount) {
            System.out.println("init flow");
            this.fxTrader = fxTrader;
            this.receiver = receiver;
            this.amount = amount;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {


            System.out.println("sending receive...");

            //gimmi which currencies ur using...
            UntrustworthyData<List> res = receive(List.class, receiver);

            List<Currency> currencies = res.unwrap(list -> list);


            SignedTransaction tx = subFlow(new CashPaymentFlow(amount, fxTrader));
            System.out.println("we have a: " + tx);
//            subFlow()

//            Object o = sendAndReceive(SignedTransaction.class, fxTrader, new ExchangeInfo(tx, receiver, amount.getQuantity(), currencies.get(0)));

            SignedTransaction ftx = subFlow(new ExchangeInitiator(new ExchangeInfo(tx, receiver, amount.getQuantity(), currencies.get(0)), fxTrader));

            System.out.println("waiting...");
            System.out.println("done");

            return ftx;
        }

        @CordaSerializable
        class ExchangeInfo {
            private SignedTransaction paidFees;
            private Party receiver;
            private Long amount;
            private Currency currency;

            public ExchangeInfo(SignedTransaction paidFees, Party receiver, Long amount, Currency currency) {
                this.paidFees = paidFees;
                this.receiver = receiver;
                this.amount = amount;
                this.currency = currency;
            }

            @Override
            public String toString() {
                return "ExchangeInfo{" +
                        "paidFees=" + paidFees +
                        ", receiver=" + receiver +
                        ", amount=" + amount +
                        ", currency=" + currency +
                        '}';
            }
        }
    }


    public static class CurrencyResponder extends FlowLogic<List<Currency>> {


        private final Party otherParty;

        public CurrencyResponder(Party otherParty) {
            this.otherParty = otherParty;
        }


        @Override
        @Suspendable
        public List<Currency> call() throws FlowException {

            System.out.println("Calling CurrencyResponder ... ");

            try {
                List<Currency> result = new ArrayList<>(getServiceHub().getVaultService().getCashBalances().keySet());

                if (result.isEmpty()) {
                    for (ServiceEntry serviceEntry : getServiceHub().getMyInfo().getAdvertisedServices()) {
                        if (serviceEntry.getInfo().component1().getId().contains("main.currency")) {
                            result.add(ContractsDSL.currency(serviceEntry.getInfo().component1().getId().split("\\.")[2]));
                        }
                    }
                }

                send(otherParty, result);

                return result;
            } catch (Exception e) {
                System.out.println("error in flow :" + e.getMessage());
            }

            return null;
        }
    }


    public static class ExchangeInitiator extends FlowLogic<SignedTransaction> {


        private MasterFxFlow.ExchangeInfo exchangeInfo;
        private Party fxTrader;

        public ExchangeInitiator(MasterFxFlow.ExchangeInfo exchangeInfo, Party fxTrader) {
            System.out.println("initing ExchangeResponder...");
            this.exchangeInfo = exchangeInfo;
            this.fxTrader = fxTrader;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            System.out.println("from ExchangeInitiator..");

            return sendAndReceive(SignedTransaction.class, fxTrader, exchangeInfo).unwrap(signedTransaction -> signedTransaction);
        }
    }

    public static class ExchangeResponder extends FlowLogic<SignedTransaction> {

        private final Party otherParty;

        public ExchangeResponder(Party otherParty) {
            this.otherParty = otherParty;
        }


        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {


            try {
                MasterFxFlow.ExchangeInfo info = receive(MasterFxFlow.ExchangeInfo.class, otherParty).unwrap(exchangeInfo -> exchangeInfo);

                float rateVal = 0;

                try {
                    String ratesJson = ExampleApi.getLastElement(getServiceHub().getVaultService().getTransactionNotes(SecureHash.sha256("rates")));
                    ObjectMapper json = JacksonSupport.createNonRpcMapper();
                    Set<CurrencyRate> rates = json.readValue(ratesJson, new TypeReference<Set<CurrencyRate>>() {
                    });

                    for (CurrencyRate rate : rates) {
                        if ("USD".equals(rate.getFrom()) && info.currency.getCurrencyCode().equals(rate.getTo())) {
                            rateVal = rate.getRate();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Amount<Issued<Currency>> amount = new Amount<>(
                        info.amount - (long) (rateVal * info.amount),
                        new Issued<>(
                                new PartyAndReference(
                                        getServiceHub().getNetworkMapCache().getNodeByLegalName("NodeC").getLegalIdentity(),
                                        OpaqueBytes.Companion.of((byte) 1)
                                ),
                                info.currency
                        )
                );

                System.out.println("execing");
                SignedTransaction signedTransaction = subFlow(new CashPaymentFlow(amount, info.receiver));

                send(otherParty, signedTransaction);

                return signedTransaction;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

}
