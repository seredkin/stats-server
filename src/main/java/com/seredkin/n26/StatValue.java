package com.seredkin.n26;

import lombok.*;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static java.math.BigDecimal.ROUND_HALF_UP;

/**
 * Where:
 * ● sum​ ​​is​ ​a​ ​double​ ​specifying​ ​the​ ​total​ ​sum​ ​of​ ​transaction​ ​value​ ​in​ ​the​ ​last​ ​60​ ​seconds
 * ● avg​ ​​is​ ​a​ ​double​ ​specifying​ ​the​ ​average​ ​amount​ ​of​ ​transaction​ ​value​ ​in​ ​the​ ​last​ ​60
 * seconds
 * ● max​ ​​is​ ​a​ ​double​ ​specifying​ ​single​ ​highest​ ​transaction​ ​value​ ​in​ ​the​ ​last​ ​60​ ​seconds
 * ● min​ ​​is​ ​a​ ​double​ ​specifying​ ​single​ ​lowest​ ​transaction​ ​value​ ​in​ ​the​ ​last​ ​60​ ​seconds
 * ● count​ ​​is​ ​a​ ​long​ ​specifying​ ​the​ ​total​ ​number​ ​of​ ​transactions​ ​happened​ ​in​ ​the​ ​last​ ​60
 * seconds
 */

@Getter
@EqualsAndHashCode(of = {"time"}) @NoArgsConstructor
public class StatValue {
    @Setter(AccessLevel.PACKAGE)
    private Long time;
    @Setter(AccessLevel.PACKAGE)
    private AtomicReference<BigDecimal> value;
    private final AtomicReference<BigDecimal> sum = new AtomicReference<>(new BigDecimal(0));
    private final AtomicReference<BigDecimal> max = new AtomicReference<>(new BigDecimal(0));
    private final AtomicReference<BigDecimal> min = new AtomicReference<>(new BigDecimal(0));
    private final AtomicReference<BigDecimal> avg = new AtomicReference<>(new BigDecimal(0));
    private final AtomicLong count = new AtomicLong(1L);

    public StatValue(@NonNull Long time,
                     @NonNull Double value) {
        this(time, new BigDecimal(value));
    }

    public StatValue(@NonNull Long time,
                     @NonNull BigDecimal bigDecimal) {
        this.time = time;
        this.value = new AtomicReference<>(bigDecimal);
        this.sum.set(bigDecimal);
        this.max.set(bigDecimal);
        this.min.set(bigDecimal);
        this.avg.set(getAvg());
    }

    public BigDecimal getAvg() {
        return sum.get().divide(new BigDecimal(this.count.get()), 2, ROUND_HALF_UP);
    }

    synchronized StatValue subtract(final StatValue value) {
        this.sum.accumulateAndGet(value.sum.get(), BigDecimal::subtract);
        this.count.decrementAndGet();
        this.max.set(max(this.max.get(), value.max.get()));
        this.min.set(min(this.min.get(), value.min.get()));
        return this;
    }

    synchronized StatValue sumWith(@NonNull final StatValue other) {
        this.count.addAndGet(other.count.get());
        this.sum.set(sum.get().add(other.sum.get()));
        this.avg.set(this.sum.get().divide(new BigDecimal(this.count.get()), 2, ROUND_HALF_UP));
        final BigDecimal currentMin = this.min.get();
        this.min.set(min(other.getMin().get(), currentMin));
        final BigDecimal currentMax = this.max.get();
        this.max.set(max(other.getMax().get(), currentMax));
        return this;
    }

    public static BigDecimal min(@NonNull BigDecimal one, @NonNull BigDecimal two) {
        return one.compareTo(two) < 0 ? one : two;
    }

    public static BigDecimal max(@NonNull BigDecimal one, @NonNull BigDecimal two) {
        return one.compareTo(two) > 0 ? one : two;
    }

    public static StatValue empty(long now) {
        return new StatValue(now, 0D) {
            {
                getSum().set(BigDecimal.ZERO);
                getCount().set(0L);
            }

            @Override
            public BigDecimal getAvg() {
                return BigDecimal.ZERO;
            }
        };
    }
}
