package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.Issued;
import net.corda.core.contracts.PartyAndReference;
import net.corda.core.crypto.Party;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.serialization.CordaSerializable;
import net.corda.core.serialization.OpaqueBytes;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.UntrustworthyData;
import net.corda.flows.CashPaymentFlow;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/**
 * Created by evilkid on 4/6/2017.
 */
public class ExampleFlow {

    public static class MasterFxFlow extends FlowLogic<Void> {

        private final Party fxTrader;
        private final Party receiver;
        private final Amount<Issued<Currency>> amount;


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

        public MasterFxFlow(Party receiver, Party fxTrader, Amount<Issued<Currency>> amount) {
            System.out.println("init flow");
            this.fxTrader = fxTrader;
            this.receiver = receiver;
            this.amount = amount;
        }

        @Override
        @Suspendable
        public Void call() throws FlowException {


            System.out.println("sending receive...");

            //gimmi which currencies ur using...
            UntrustworthyData<List> res = receive(List.class, receiver);

            List<Currency> currencies = res.unwrap(list -> list);


            SignedTransaction tx = subFlow(new CashPaymentFlow(amount, fxTrader));
            System.out.println("we have a: " + tx);
//            subFlow()

//            Object o = sendAndReceive(SignedTransaction.class, fxTrader, new ExchangeInfo(tx, receiver, amount.getQuantity(), currencies.get(0)));

            Object o = subFlow(new ExchangeInitiator(new ExchangeInfo(tx, receiver, amount.getQuantity(), currencies.get(0)), fxTrader));

            System.out.println("got :" + o);

            return null;
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


            MasterFxFlow.ExchangeInfo info = receive(MasterFxFlow.ExchangeInfo.class, otherParty).unwrap(exchangeInfo -> exchangeInfo);

            //if(info.paidFees != null && info.paidFees exists) check if the node really send some $$

            Amount<Issued<Currency>> amount = new Amount<>(
                    info.amount,
                    new Issued<>(
                            new PartyAndReference(
                                    getServiceHub().getNetworkMapCache().getNodeByLegalName("NodeC").getLegalIdentity(),
                                    OpaqueBytes.Companion.of((byte) 1)
                            ),
                            info.currency
                    )
            );

            System.out.println(amount);

            CashPaymentFlow cashPaymentFlow = new CashPaymentFlow(amount, info.receiver);

            SignedTransaction tx = subFlow(cashPaymentFlow);
            System.out.println("done transaction: " + tx);
            return tx;
        }
    }

}
