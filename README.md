//  New Code Shiva Nandini major change : Change data type from List to queue, remove the latch . Used same DB read & locking 

package com.sarvatra.rtsp.service;

import com.sarvatra.common.util.SarvatraDateUtil;
import com.sarvatra.rtsp.cache.InstituteCache;
import com.sarvatra.rtsp.ee.MerchantPayoutDetails;
import com.sarvatra.rtsp.manager.MerchantPayoutManager;
import com.sarvatra.rtsp.server.InstituteCacheHelper;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jpos.util.LogEvent;
import org.jpos.util.Logger;

import java.util.*;
import java.util.concurrent.*;

public class PayoutTxnProcessService extends BaseThreadBasedPollingService {

    @Override
    public String toString() {
        return super.toString() + "~" + getName();
    }

    private BlockingQueue<MerchantPayoutDetails> blockingQueue = new LinkedBlockingQueue<>();
    private InstituteCache instituteCache = InstituteCacheHelper.getInstitute(institute);

    public void process() throws InterruptedException {
        try {

            List<MerchantPayoutDetails> payoutTxnList = org.jpos.ee.DB.exec(db ->
                    new MerchantPayoutManager(db).getMerchantPayoutTransactionList(new SarvatraDateUtil().getStartOfDay()));

             logPayoutSummary(payoutTxnList);


            if (payoutTxnList.isEmpty()) {
                getLog().info("No payout transactions found");
                return;
            }

            //Add all elements to the blocking queue
            blockingQueue.addAll(payoutTxnList);

            // Queue payout transactions
            ExecutorService executorService = getFixedThreadPool();
            for(int index = 0; index < BaseThreadBasedPollingService.MAX_THREADS; index++) {
                executorService.execute(this::processQueue);
            }

            executorService.shutdown(); //Gracefully shutdown the thread pool
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS); //Wait forever until all the tasks are finished
//            CountDownLatch latch = new CountDownLatch(payoutTxnList.size());
//            getLog().info("Payout Txn Details Size :"+payoutTxnList.size());
//            payoutTxnList.forEach(
//                    payoutDetails -> getThreadPoolExecutor()
//                            .execute(new MerchantPayoutFeature
//                                    (payoutDetails, instituteCache, transactionName, transactionManagerQueue, latch, getLog())));
//
//            latch.await();
            getLog().info("Done with merchant payout service");

        } catch (Exception ex) {
            getLog().info("###### Inside catch block merchant payout ");
            getLog().info("Exception occurred: " + ExceptionUtils.getStackTrace(ex));
        }
    }

    private void processQueue() {
        //Validate the queue
        if(blockingQueue.size() != 0) {
            try {
                MerchantPayoutDetails payoutDetails = blockingQueue.poll(5, TimeUnit.SECONDS);
                if(payoutDetails != null) {
                    new MerchantPayoutFeature(payoutDetails, instituteCache,transactionName, transactionManagerQueue, new CountDownLatch(1), getLog()).run();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }else {
            getLog().info("#### Merchant Payout queue is empty.");
        }
        //
    }



    private void logPayoutSummary(List<MerchantPayoutDetails> payoutTxnList) {
        LogEvent evt = getLog().createLogEvent("Merchant Payout Summary");
        evt.addMessage(LOG_SEPARATOR);
        evt.addMessage("Start Date        | " + new SarvatraDateUtil().getStartOfDay());
        evt.addMessage("Payout count      | " + payoutTxnList.size());
        evt.addMessage("Transaction Name  | " + this.transactionName);
        evt.addMessage(LOG_SEPARATOR);
        Logger.log(evt);
    }
}

//  Old Code // Modified Original : Control at split file level , run once and record error run till file is processed . 



package com.sarvatra.rtsp.service;

import com.sarvatra.common.util.SarvatraDateUtil;
import com.sarvatra.rtsp.cache.InstituteCache;
import com.sarvatra.rtsp.ee.MerchantPayoutDetails;
import com.sarvatra.rtsp.manager.MerchantPayoutManager;
import com.sarvatra.rtsp.server.InstituteCacheHelper;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jpos.util.LogEvent;
import org.jpos.util.Logger;

import java.util.*;
import java.util.concurrent.CountDownLatch;

public class PayoutTxnProcessService extends BaseThreadBasedPollingService {

    @Override
    public String toString() {
        return super.toString() + "~" + getName();
    }

    public void process() throws InterruptedException {
        InstituteCache instituteCache = InstituteCacheHelper.getInstitute(institute);
        try {
            List<MerchantPayoutDetails> payoutTxnList = org.jpos.ee.DB.exec(db ->
                    new MerchantPayoutManager(db).getMerchantPayoutTransactionList(new SarvatraDateUtil().getStartOfDay()));

             logPayoutSummary(payoutTxnList);

            if (payoutTxnList.isEmpty()) {
                getLog().info("No payout transactions found");
                return;
            }

            // Queue payout transactions
            CountDownLatch latch = new CountDownLatch(payoutTxnList.size());
            getLog().info("Payout Txn Details Size :"+payoutTxnList.size());
            payoutTxnList.forEach(payoutDetails -> getThreadPoolExecutor().execute(new MerchantPayoutFeature(payoutDetails, instituteCache, transactionName, transactionManagerQueue, latch, getLog())));

            latch.await();
            getLog().info("Done with merchant payout service");

        } catch (Exception ex) {
            getLog().info("Exception occurred: " + ExceptionUtils.getStackTrace(ex));
        }
    }


    private void logPayoutSummary(List<MerchantPayoutDetails> payoutTxnList) {
        LogEvent evt = getLog().createLogEvent("Merchant Payout Summary");
        evt.addMessage(LOG_SEPARATOR);
        evt.addMessage("Start Date        | " + new SarvatraDateUtil().getStartOfDay());
        evt.addMessage("Payout count      | " + payoutTxnList.size());
        evt.addMessage("Transaction Name  | " + this.transactionName);
        evt.addMessage(LOG_SEPARATOR);
        Logger.log(evt);
    }
}
-----------------------------
// Gagan View LatchCountdown is better . Queue is for a dynamic length list , here the list length is known. Remove split file approach , change DB read approach 

package com.sarvatra.rtsp.service;

import com.sarvatra.common.util.SarvatraDateUtil;
import com.sarvatra.rtsp.cache.InstituteCache;
import com.sarvatra.rtsp.ee.MerchantPayoutDetails;
import com.sarvatra.rtsp.manager.MerchantPayoutManager;
import com.sarvatra.rtsp.server.InstituteCacheHelper;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jpos.util.LogEvent;
import org.jpos.util.Logger;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class PayoutTxnProcessService extends BaseThreadBasedPollingService {

    private static final int MaxThread = 30;          // Maximum threads that can be used
    private static final int OptimumNuTxnPerThread = 1000; // Records each thread processes at a time

    @Override
    public String toString() {
        return super.toString() + "~" + getName();
    }

    public void process() throws InterruptedException {
        InstituteCache instituteCache = InstituteCacheHelper.getInstitute(institute);
        
        try {
            int totalRemainingRecords = getTotalRemainingRecords();
            int offset = 0;

            while (offset < totalRemainingRecords) {
                // Dynamically calculate the number of threads based on remaining records
                int remainingRecords = totalRemainingRecords - offset;
                int threadsNeeded = Math.min(MaxThread, (int) Math.ceil((double) remainingRecords / OptimumNuTxnPerThread));
                int batchSize = threadsNeeded * OptimumNuTxnPerThread;

                // Fetch the next batch of transactions based on calculated offset and batch size
                List<MerchantPayoutDetails> payoutTxnList = fetchTransactions(offset, batchSize);
                offset += batchSize;

                // Log the summary of this batch
                logPayoutSummary(payoutTxnList);

                // If there are no transactions, exit
                if (payoutTxnList.isEmpty()) {
                    getLog().info("No more payout transactions to process.");
                    break;
                }

                // Queue payout transactions for processing with threads
                CountDownLatch latch = new CountDownLatch(payoutTxnList.size());
                payoutTxnList.forEach(payoutDetails -> 
                        getThreadPoolExecutor().execute(new MerchantPayoutFeature(payoutDetails, instituteCache, transactionName, transactionManagerQueue, latch, getLog())));

                // Wait for all threads to finish processing the current batch
                latch.await();
                getLog().info("Batch processed successfully.");
            }

            getLog().info("All transactions have been processed.");

        } catch (Exception ex) {
            getLog().error("Error during transaction processing: " + ExceptionUtils.getStackTrace(ex));
        }
    }

    // Fetch transactions from the database using LIMIT and OFFSET for pagination
    private List<MerchantPayoutDetails> fetchTransactions(int offset, int batchSize) {
        return org.jpos.ee.DB.exec(db -> 
                new MerchantPayoutManager(db).getMerchantPayoutTransactionList(new SarvatraDateUtil().getStartOfDay(), offset, batchSize));
    }

    // Log the summary of the payout transactions
    private void logPayoutSummary(List<MerchantPayoutDetails> payoutTxnList) {
        LogEvent evt = getLog().createLogEvent("Merchant Payout Summary");
        evt.addMessage(LOG_SEPARATOR);
        evt.addMessage("Start Date        | " + new SarvatraDateUtil().getStartOfDay());
        evt.addMessage("Payout count      | " + payoutTxnList.size());
        evt.addMessage("Transaction Name  | " + this.transactionName);
        evt.addMessage(LOG_SEPARATOR);
        Logger.log(evt);
    }

    // Fetch the total number of remaining records
    private int getTotalRemainingRecords() {
        // This method should return the total number of records that need processing.
        // You can modify this logic to query the DB for the total number of records.
        return org.jpos.ee.DB.exec(db -> 
                new MerchantPayoutManager(db).getTotalMerchantPayoutTransactionCount(new SarvatraDateUtil().getStartOfDay()));
    }
}
