package com.yammer.metrics.core;

import com.yammer.metrics.annotation.Publish;
import com.yammer.metrics.stats.ExponentiallyDecayingSample;
import com.yammer.metrics.stats.Sample;
import com.yammer.metrics.stats.Snapshot;
import com.yammer.metrics.stats.UniformSample;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Math.sqrt;

/**
 * A metric which calculates the distribution of a value.
 *
 * @see <a href="http://www.johndcook.com/standard_deviation.html">Accurately computing running
 *      variance</a>
 */
public class Histogram implements Metric, Sampling, Summarizable {
    private static final int DEFAULT_SAMPLE_SIZE = 1028;
    private static final double DEFAULT_ALPHA = 0.015;

    /**
     * The type of sampling the histogram should be performing.
     */
    enum SampleType {
        /**
         * Uses a uniform sample of 1028 elements, which offers a 99.9% confidence level with a 5%
         * margin of error assuming a normal distribution.
         */
        UNIFORM {
            @Override
            public Sample newSample() {
                return new UniformSample(DEFAULT_SAMPLE_SIZE);
            }
        },

        /**
         * Uses an exponentially decaying sample of 1028 elements, which offers a 99.9% confidence
         * level with a 5% margin of error assuming a normal distribution, and an alpha factor of
         * 0.015, which heavily biases the sample to the past 5 minutes of measurements.
         */
        BIASED {
            @Override
            public Sample newSample() {
                return new ExponentiallyDecayingSample(DEFAULT_SAMPLE_SIZE, DEFAULT_ALPHA);
            }
        };

        public abstract Sample newSample();
    }

    /**
     * Cache arrays for the variance calculation, so as to avoid memory allocation.
     */
    private static class ArrayCache extends ThreadLocal<double[]> {
        @Override
        protected double[] initialValue() {
            return new double[2];
        }
    }

    private final Sample sample;
    private final AtomicLong min = new AtomicLong();
    private final AtomicLong max = new AtomicLong();
    private final AtomicLong sum = new AtomicLong();
    // These are for the Welford algorithm for calculating running variance
    // without floating-point doom.
    private final AtomicReference<double[]> variance =
            new AtomicReference<double[]>(new double[]{-1, 0}); // M, S
    private final AtomicLong count = new AtomicLong();
    private final ArrayCache arrayCache = new ArrayCache();

    /**
     * Creates a new {@link Histogram} with the given sample type.
     *
     * @param type the type of sample to use
     */
    Histogram(SampleType type) {
        this(type.newSample());
    }

    /**
     * Creates a new {@link Histogram} with the given sample.
     *
     * @param sample the sample to create a histogram from
     */
    Histogram(Sample sample) {
        this.sample = sample;
        clear();
    }

    /**
     * Clears all recorded values.
     */
    public void clear() {
        sample.clear();
        count.set(0);
        max.set(Long.MIN_VALUE);
        min.set(Long.MAX_VALUE);
        sum.set(0);
        variance.set(new double[]{-1, 0});
    }

    /**
     * Adds a recorded value.
     *
     * @param value the length of the value
     */
    public void update(int value) {
        update((long) value);
    }

    /**
     * Adds a recorded value.
     *
     * @param value the length of the value
     */
    public void update(long value) {
        count.incrementAndGet();
        sample.update(value);
        setMax(value);
        setMin(value);
        sum.getAndAdd(value);
        updateVariance(value);
    }

    /**
     * Returns the number of values recorded.
     *
     * @return the number of values recorded
     */
    @Publish
    public long getCount() {
        return count.get();
    }

    /* (non-Javadoc)
     * @see com.yammer.metrics.core.Summarizable#getMax()
     */
    @Override
    @Publish
    public double getMax() {
        if (getCount() > 0) {
            return max.get();
        }
        return 0.0;
    }

    /* (non-Javadoc)
     * @see com.yammer.metrics.core.Summarizable#getMin()
     */
    @Override
    @Publish
    public double getMin() {
        if (getCount() > 0) {
            return min.get();
        }
        return 0.0;
    }

    /* (non-Javadoc)
     * @see com.yammer.metrics.core.Summarizable#getMean()
     */
    @Override
    @Publish
    public double getMean() {
        if (getCount() > 0) {
            return sum.get() / (double) getCount();
        }
        return 0.0;
    }

    /* (non-Javadoc)
     * @see com.yammer.metrics.core.Summarizable#getStdDev()
     */
    @Override
    @Publish
    public double getStdDev() {
        if (getCount() > 0) {
            return sqrt(variance());
        }
        return 0.0;
    }

    /* (non-Javadoc)
     * @see com.yammer.metrics.core.Summarizable#getSum()
     */
    @Override
    @Publish
    public double getSum() {
        return (double) sum.get();
    }

    @Override
    public Snapshot getSnapshot() {
        return sample.getSnapshot();
    }

    /**
     * Returns the median value in the distribution.
     *
     * @return the median value in the distribution
     */
    @Publish
    public double getMedian() {
        return getSnapshot().getMedian();
    }

    /**
     * Returns the value at the 75th percentile in the distribution.
     *
     * @return the value at the 75th percentile in the distribution
     */
    @Publish
    public double get75thPercentile() {
        return getSnapshot().get75thPercentile();
    }

    /**
     * Returns the value at the 95th percentile in the distribution.
     *
     * @return the value at the 95th percentile in the distribution
     */
    @Publish
    public double get95thPercentile() {
        return getSnapshot().get95thPercentile();
    }

    /**
     * Returns the value at the 98th percentile in the distribution.
     *
     * @return the value at the 98th percentile in the distribution
     */
    @Publish
    public double get98thPercentile() {
        return getSnapshot().get98thPercentile();
    }

    /**
     * Returns the value at the 99th percentile in the distribution.
     *
     * @return the value at the 99th percentile in the distribution
     */
    @Publish
    public double get99thPercentile() {
        return getSnapshot().get99thPercentile();
    }

    /**
     * Returns the value at the 99.9th percentile in the distribution.
     *
     * @return the value at the 99.9th percentile in the distribution
     */
    @Publish
    public double get999thPercentile() {
        return getSnapshot().get999thPercentile();
    }

    private double variance() {
        if (getCount() <= 1) {
            return 0.0;
        }
        return variance.get()[1] / (getCount() - 1);
    }

    private void setMax(long potentialMax) {
        boolean done = false;
        while (!done) {
            final long currentMax = max.get();
            done = currentMax >= potentialMax || max.compareAndSet(currentMax, potentialMax);
        }
    }

    private void setMin(long potentialMin) {
        boolean done = false;
        while (!done) {
            final long currentMin = min.get();
            done = currentMin <= potentialMin || min.compareAndSet(currentMin, potentialMin);
        }
    }

    private void updateVariance(long value) {
        boolean done = false;
        while (!done) {
            final double[] oldValues = variance.get();
            final double[] newValues = arrayCache.get();
            if (oldValues[0] == -1) {
                newValues[0] = value;
                newValues[1] = 0;
            } else {
                final double oldM = oldValues[0];
                final double oldS = oldValues[1];

                final double newM = oldM + ((value - oldM) / getCount());
                final double newS = oldS + ((value - oldM) * (value - newM));

                newValues[0] = newM;
                newValues[1] = newS;
            }
            done = variance.compareAndSet(oldValues, newValues);
            if (done) {
                // recycle the old array into the cache
                arrayCache.set(oldValues);
            }
        }
    }

    @Override
    public <T> void processWith(MetricProcessor<T> processor, MetricName name, T context) throws Exception {
        processor.processHistogram(name, this, context);
    }
}
