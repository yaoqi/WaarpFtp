/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.traffic;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TrafficCounter is associated with {@link AbstractTrafficShapingHandler}.<br>
 * <br>
 * A TrafficCounter has for goal to count the traffic in order to enable to limit the traffic or not,
 * globally or per channel. It compute statistics on read and written bytes at the specified
 * interval and call back the {@link AbstractTrafficShapingHandler} doAccounting method at every
 * specified interval. If this interval is set to 0, therefore no accounting will be done and only
 * statistics will be computed at each receive or write operations.
 */
public class TrafficCounter {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(TrafficCounter.class);

    /**
     * Current written bytes
     */
    private final AtomicLong currentWrittenBytes = new AtomicLong();

    /**
     * Current read bytes
     */
    private final AtomicLong currentReadBytes = new AtomicLong();

    /**
     * Last writing time during current check interval
     */
    private long writingTime = System.currentTimeMillis();

    /**
     * Last reading delay during current check interval
     */
    private long readingTime = System.currentTimeMillis();

    /**
     * Long life written bytes
     */
    private final AtomicLong cumulativeWrittenBytes = new AtomicLong();

    /**
     * Long life read bytes
     */
    private final AtomicLong cumulativeReadBytes = new AtomicLong();

    /**
     * Last Time where cumulative bytes where reset to zero
     */
    private long lastCumulativeTime;

    /**
     * Last writing bandwidth
     */
    private long lastWriteThroughput;

    /**
     * Last reading bandwidth
     */
    private long lastReadThroughput;

    /**
     * Last Time Check taken
     */
    private final AtomicLong lastTime = new AtomicLong();

    /**
     * Last written bytes number during last check interval
     */
    private long lastWrittenBytes;

    /**
     * Last read bytes number during last check interval
     */
    private long lastReadBytes;

    /**
     * Last future writing time during last check interval
     */
    private long lastWritingTime = System.currentTimeMillis();

    /**
     * Last reading time during last check interval
     */
    private long lastReadingTime = System.currentTimeMillis();

    /**
     * Real written bytes
     */
    private final AtomicLong realWrittenBytes = new AtomicLong();

    /**
     * Real writing bandwidth
     */
    private long realWriteThroughput;

    /**
     * Delay between two captures
     */
    final AtomicLong checkInterval = new AtomicLong(
            AbstractTrafficShapingHandler.DEFAULT_CHECK_INTERVAL);

    // default 1 s

    /**
     * Name of this Monitor
     */
    final String name;

    /**
     * The associated TrafficShapingHandler
     */
    private final AbstractTrafficShapingHandler trafficShapingHandler;

    /**
     * One Timer for all Counter
     */
    private final Timer timer;  // replace executor
    /**
     * Monitor created once in start()
     */
    private TimerTask timerTask;
    /**
     * used in stop() to cancel the timer
     */
   private volatile Timeout timeout;

    /**
     * Is Monitor active
     */
    final AtomicBoolean monitorActive = new AtomicBoolean();

    /**
     * Class to implement monitoring at fix delay
     *
     */
    private static class TrafficMonitoringTask implements TimerTask {
        /**
         * The associated TrafficShapingHandler
         */
        private final AbstractTrafficShapingHandler trafficShapingHandler1;

        /**
         * The associated TrafficCounter
         */
        private final TrafficCounter counter;

        protected TrafficMonitoringTask(
                AbstractTrafficShapingHandler trafficShapingHandler,
                TrafficCounter counter) {
            trafficShapingHandler1 = trafficShapingHandler;
            this.counter = counter;
        }

        public void run(Timeout timeout) throws Exception {
            if (!counter.monitorActive.get()) {
                return;
            }
            long endTime = System.currentTimeMillis();
            counter.resetAccounting(endTime);
            if (trafficShapingHandler1 != null) {
                trafficShapingHandler1.doAccounting(counter);
            }

            counter.timer.newTimeout(this, counter.checkInterval.get(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Start the monitoring process
     */
    public void start() {
        synchronized (lastTime) {
            if (monitorActive.get()) {
                return;
            }
            lastTime.set(System.currentTimeMillis());
            if (checkInterval.get() > 0) {
                monitorActive.set(true);
                timerTask = new TrafficMonitoringTask(trafficShapingHandler, this);
                timeout =
                    timer.newTimeout(timerTask, checkInterval.get(), TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Stop the monitoring process
     */
    public void stop() {
        synchronized (lastTime) {
            if (!monitorActive.get()) {
                return;
            }
            monitorActive.set(false);
            resetAccounting(System.currentTimeMillis());
            if (trafficShapingHandler != null) {
                trafficShapingHandler.doAccounting(this);
            }
            if (timeout != null) {
                timeout.cancel();
            }
        }
    }

    /**
     * Reset the accounting on Read and Write
     */
    void resetAccounting(long newLastTime) {
        synchronized (lastTime) {
            long interval = newLastTime - lastTime.getAndSet(newLastTime);
            if (interval == 0) {
                // nothing to do
                return;
            }
            lastReadBytes = currentReadBytes.getAndSet(0);
            lastWrittenBytes = currentWrittenBytes.getAndSet(0);
            lastReadThroughput = lastReadBytes * 1000 / interval;
            // nb byte / checkInterval in ms * 1000 (1s)
            lastWriteThroughput = lastWrittenBytes * 1000 / interval;
            // nb byte / checkInterval in ms * 1000 (1s)
            realWriteThroughput = realWrittenBytes.getAndSet(0) * 1000 / interval;
            lastWritingTime = Math.max(lastWritingTime, writingTime);
            lastReadingTime = Math.max(lastReadingTime, readingTime);
        }
    }

    /**
     * Constructor with the {@link AbstractTrafficShapingHandler} that hosts it, the Timer to use, its
     * name, the checkInterval between two computations in millisecond
     * @param trafficShapingHandler the associated AbstractTrafficShapingHandler
     * @param timer
     *            Could be a HashedWheelTimer
     * @param name
     *            the name given to this monitor
     * @param checkInterval
     *            the checkInterval in millisecond between two computations
     */
    public TrafficCounter(AbstractTrafficShapingHandler trafficShapingHandler,
            Timer timer, String name, long checkInterval) {
        this.trafficShapingHandler = trafficShapingHandler;
        this.timer = timer;
        this.name = name;
        lastCumulativeTime = System.currentTimeMillis();
        configure(checkInterval);
    }

    /**
     * Change checkInterval between
     * two computations in millisecond
     */
    public void configure(long newcheckInterval) {
        long newInterval = newcheckInterval / 10 * 10;
        if (checkInterval.get() != newInterval) {
            checkInterval.set(newInterval);
            if (newInterval <= 0) {
                stop();
                // No more active monitoring
                lastTime.set(System.currentTimeMillis());
            } else {
                // Start if necessary
                start();
            }
        }
    }

    /**
     * Computes counters for Read.
     *
     * @param recv
     *            the size in bytes to read
     */
    void bytesRecvFlowControl(long recv) {
        currentReadBytes.addAndGet(recv);
        cumulativeReadBytes.addAndGet(recv);
    }

    /**
     * Computes counters for Write.
     *
     * @param write
     *            the size in bytes to write
     */
    void bytesWriteFlowControl(long write) {
        currentWrittenBytes.addAndGet(write);
        cumulativeWrittenBytes.addAndGet(write);
    }

    /**
     * Computes counters for Real Write.
     *
     * @param write
     *            the size in bytes to write
     * @param schedule
     *            the time when this write was scheduled
     */
    void bytesRealWriteFlowControl(long write) {
        realWrittenBytes.addAndGet(write);
    }

    /**
     *
     * @return the current checkInterval between two computations of traffic counter
     *         in millisecond
     */
    public long getCheckInterval() {
        return checkInterval.get();
    }

    /**
     *
     * @return the Read Throughput in bytes/s computes in the last check interval
     */
    public long getLastReadThroughput() {
        return lastReadThroughput;
    }

    /**
     *
     * @return the Write Throughput in bytes/s computes in the last check interval
     */
    public long getLastWriteThroughput() {
        return lastWriteThroughput;
    }

    /**
     *
     * @return the number of bytes read during the last check Interval
     */
    public long getLastReadBytes() {
        return lastReadBytes;
    }

    /**
     *
     * @return the number of bytes written during the last check Interval
     */
    public long getLastWrittenBytes() {
        return lastWrittenBytes;
    }

    /**
    *
    * @return the current number of bytes read since the last checkInterval
    */
    public long getCurrentReadBytes() {
        return currentReadBytes.get();
    }

    /**
     *
     * @return the current number of bytes written since the last check Interval
     */
    public long getCurrentWrittenBytes() {
        return currentWrittenBytes.get();
    }

    /**
     * @return the Time in millisecond of the last check as of System.currentTimeMillis()
     */
    public long getLastTime() {
        return lastTime.get();
    }

    /**
     * @return the cumulativeWrittenBytes
     */
    public long getCumulativeWrittenBytes() {
        return cumulativeWrittenBytes.get();
    }

    /**
     * @return the cumulativeReadBytes
     */
    public long getCumulativeReadBytes() {
        return cumulativeReadBytes.get();
    }

    /**
     * @return the lastCumulativeTime in millisecond as of System.currentTimeMillis()
     * when the cumulative counters were reset to 0.
     */
    public long getLastCumulativeTime() {
        return lastCumulativeTime;
    }

    /**
     * @return the realWrittenBytes
     */
    public AtomicLong getRealWrittenBytes() {
        return realWrittenBytes;
    }

    /**
     * @return the realWriteThroughput
     */
    public long getRealWriteThroughput() {
        return realWriteThroughput;
    }

    /**
     * Reset both read and written cumulative bytes counters and the associated time.
     */
    public void resetCumulativeTime() {
        lastCumulativeTime = System.currentTimeMillis();
        cumulativeReadBytes.set(0);
        cumulativeWrittenBytes.set(0);
    }

    /**
     * Returns the time to wait (if any) for the given length message, using the given limitTraffic and the max wait
     * time
     *
     * @param size
     *            the recv size
     * @param limitTraffic
     *            the traffic limit in bytes per second
     * @param maxTime
     *            the max time in ms to wait in case of excess of traffic
     * @return the current time to wait (in ms) if needed for Read operation
     */
    public long readTimeToWait(final long size, final long limitTraffic, final long maxTime) {
        final long now = System.currentTimeMillis();
        bytesRecvFlowControl(size);
        if (size == 0 || limitTraffic == 0) {
            return 0;
        }
        final long lastTimeCheck = lastTime.get();
        final long interval = now - lastTimeCheck;
        long pastDelay = Math.max(lastReadingTime - lastTimeCheck, 0);
        long sum = currentReadBytes.get();
        if (interval > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
            // Enough interval time to compute shaping
            long time = (sum * 1000 / limitTraffic - interval + pastDelay) / 10 * 10;
            if (time > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Time: " + time + ":" + sum + ":" + interval + ":" + pastDelay);
                }
                if (time > maxTime && now + time - readingTime > maxTime) {
                    time = maxTime;
                }
                readingTime = Math.max(readingTime, now + time);
                return time;
            }
            readingTime = Math.max(readingTime, now);
            return 0;
        }
        // take the last read interval check to get enough interval time
        long lastsum = sum + lastReadBytes;
        long lastinterval = interval + checkInterval.get();
        long time = (lastsum * 1000 / limitTraffic - lastinterval + pastDelay) / 10 * 10;
        if (time > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
            if (logger.isDebugEnabled()) {
                logger.debug("Time: " + time + ":" + lastsum + ":" + lastinterval + ":" + pastDelay);
            }
            if (time > maxTime && now + time - readingTime > maxTime) {
                time = maxTime;
            }
            readingTime = Math.max(readingTime, now + time);
            return time;
        }
        readingTime = Math.max(readingTime, now);
        return 0;
    }

    /**
     * Returns the time to wait (if any) for the given length message, using the given limitTraffic and
     * the max wait time
     *
     * @param size
     *            the write size
     * @param limitTraffic
     *            the traffic limit in bytes per second
     * @param maxTime
     *            the max time in ms to wait in case of excess of traffic
     * @return the current time to wait (in ms) if needed for Write operation
     */
    public synchronized long writeTimeToWait(final long size, final long limitTraffic, final long maxTime) {
        bytesWriteFlowControl(size);
        if (size == 0 || limitTraffic == 0) {
            return 0;
        }
        final long now = System.currentTimeMillis();
        final long lastTimeCheck = lastTime.get();
        final long interval = now - lastTimeCheck;
        long pastDelay = Math.max(lastWritingTime - lastTimeCheck, 0);
        long sum = currentWrittenBytes.get();
        if (interval > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
            // Enough interval time to compute shaping
            long time = (sum * 1000 / limitTraffic - interval + pastDelay) / 10 * 10;
            if (time > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Time: " + time + ":" + sum + ":" + interval + ":" + pastDelay);
                }
                if (time > maxTime && now + time - writingTime > maxTime) {
                    time = maxTime;
                }
                writingTime = Math.max(writingTime, now + time);
                return time;
            }
            writingTime = Math.max(writingTime, now);
            return 0;
        }
        // take the last write interval check to get enough interval time
        long lastsum = sum + lastWrittenBytes;
        long lastinterval = interval + checkInterval.get();
        long time = (lastsum * 1000 / limitTraffic - lastinterval + pastDelay) / 10 * 10;
        if (time > AbstractTrafficShapingHandler.MINIMAL_WAIT) {
            if (logger.isDebugEnabled()) {
                logger.debug("Time: " + time + ":" + lastsum + ":" + lastinterval + ":" + pastDelay);
            }
            if (time > maxTime && now + time - writingTime > maxTime) {
                time = maxTime;
            }
            writingTime = Math.max(writingTime, now + time);
            return time;
        }
        writingTime = Math.max(writingTime, now);
        return 0;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * String information
     */
    @Override
    public String toString() {
        return "Monitor " + name + " Current Speed Read: " +
                (lastReadThroughput >> 10) + " KB/s, Asked Write: " +
                (lastWriteThroughput >> 10) + " KB/s, " +
                "Real Write: " + (realWriteThroughput >> 10) + " KB/s, Current Read: " +
                (currentReadBytes.get() >> 10) + " KB, Current Asked Write: " +
                (currentWrittenBytes.get() >> 10) + " KB, " +
                "Current real Write: " + (realWrittenBytes.get() >> 10) + " KB";
    }
}
