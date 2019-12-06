package com.andriyuk.backendtest.account.v0_1.service;

import com.andriyuk.backendtest.api.v0_1.account.Account;
import com.andriyuk.backendtest.api.v0_1.account.Currency;
import com.andriyuk.backendtest.api.v0_1.transfer.TransferRequest;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.andriyuk.backendtest.account.v0_1.service.TestHelper.getRandomAccountTemplate;
import static com.andriyuk.backendtest.account.v0_1.service.TestHelper.getRandomBigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test suite for testing concurrent account operations
 */
@MicronautTest
public class ConcurrencyTest {

    @Inject
    AccountService accountService;

    @Inject
    ConcurrencyTestTransferService concurrencyTransferService;

    private Executor simpleExecutor = (runnable -> new Thread(runnable).start());

    @MicronautTest
    @Test
    public void concurrentTransferTest() throws InterruptedException {
        CountDownLatch transferLatch = new CountDownLatch(1);
        CountDownLatch resultLatch = new CountDownLatch(2);

        BigDecimal transferAmount = BigDecimal.TEN;
        Account sourceAccount = accountService.create(getRandomAccountTemplate(transferAmount, Currency.EUR));
        Account destinationAccount = accountService.create(getRandomAccountTemplate(getRandomBigDecimal(), Currency.EUR));

        TransferRequest request = new TransferRequest(sourceAccount, destinationAccount, transferAmount);
        //Do parallel create operations
        simpleExecutor.execute(() -> concurrencyTransferService.waitForWithdrawAndTransfer(request, transferLatch, resultLatch));
        simpleExecutor.execute(() -> concurrencyTransferService.withdrawWaitAndDeposit(request, transferLatch, resultLatch));

        resultLatch.await(); //Wait until both parallel create operations are completed

        assertEquals(transferAmount.subtract(transferAmount), accountService.getById(sourceAccount.getId()).getBalance(),
                "Non zero account balance means lack of thread safety.");

    }

    //todo fix me
    //@MicronautTest
    //@Test
    public void deadlockTransferTest() throws InterruptedException {
        CountDownLatch withdrawLatch = new CountDownLatch(2);

        BigDecimal transferAmount = BigDecimal.TEN;
        BigDecimal initialAmount = BigDecimal.TEN.multiply(BigDecimal.TEN);

        Account accountA = accountService.create(getRandomAccountTemplate(initialAmount, Currency.EUR));
        Account accountB = accountService.create(getRandomAccountTemplate(initialAmount, Currency.EUR));

        TransferRequest requestAB = new TransferRequest(accountA, accountB, transferAmount);
        TransferRequest requestBA = new TransferRequest(accountB, accountA, transferAmount);

        Callable<Void> callableAB = () -> {concurrencyTransferService.withdrawAndWait(requestAB, withdrawLatch); return null;};
        Callable<Void> callableBA = () -> {concurrencyTransferService.withdrawAndWait(requestBA, withdrawLatch); return null;};

        Executors.newFixedThreadPool(2).invokeAll(List.of(callableAB, callableBA));

        assertEquals(initialAmount, accountService.getById(accountA.getId()).getBalance());
        assertEquals(initialAmount, accountService.getById(accountB.getId()).getBalance());
    }
}
