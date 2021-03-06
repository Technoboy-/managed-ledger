/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bookkeeper.mledger.util;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Create stats buckets to have frequency distribution of samples.
 * 
 */
public class StatsBuckets {
    private final double[] boundaries;
    private final long[] buckets;
    private long count = 0;
    private double sum = Double.NaN;
    private double min = Double.NaN;
    private double max = Double.NaN;

    public StatsBuckets(double... boundaries) {
        checkArgument(boundaries.length > 0);
        checkArgument(isSorted(boundaries), "Boundaries array must be sorted");
        this.boundaries = boundaries;
        this.buckets = new long[boundaries.length + 1];
    }

    public void addValue(double value) {
        int i = 0;

        while (i < boundaries.length && value > boundaries[i]) {
            ++i;
        }

        synchronized (this) {
            buckets[i]++;
            if (Double.isNaN(sum)) {
                sum = 0;
            }

            sum += value;
            count++;

            if (Double.isNaN(min) || value < min) {
                min = value;
            }

            if (Double.isNaN(max) || value > max) {
                max = value;
            }
        }
    }

    public synchronized long[] getBuckets() {
        return buckets;
    }

    public synchronized long getCount() {
        return count;
    }

    public synchronized double getSum() {
        return sum;
    }

    public synchronized double getAvg() {
        return sum / count;
    }

    public synchronized double getMin() {
        return min;
    }

    public synchronized double getMax() {
        return max;
    }

    private boolean isSorted(double[] array) {
        double previous = Double.NEGATIVE_INFINITY;

        for (double value : array) {
            if (value < previous) {
                return false;
            }

            previous = value;
        }

        return true;
    }
}
