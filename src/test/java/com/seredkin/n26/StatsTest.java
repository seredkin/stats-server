package com.seredkin.n26;

import lombok.SneakyThrows;
import org.joda.time.DateTime;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.seredkin.n26.StatValue.max;
import static com.seredkin.n26.StatValue.min;
import static com.seredkin.n26.StatsService.MINUTE;
import static java.lang.System.currentTimeMillis;
import static java.math.RoundingMode.HALF_UP;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class StatsTest {

    @Test public void testMinMax(){
        final BigDecimal lower = new BigDecimal(Integer.MIN_VALUE);
        final BigDecimal upper = new BigDecimal(Integer.MAX_VALUE);
        StatValue value = new StatValue(0L, 0D);
        assertThat(min(lower, value.getValue().get()), equalTo(lower));
        assertThat(max(upper, value.getValue().get()), equalTo(upper));
    }


    @Test
    public void testAdd2() {
        final StatsServiceImpl service = new StatsServiceImpl();
        long theTime = currentTimeMillis();
        StatTx tx = new StatTx(theTime, 1D);
        StatValue first = service.add(tx);
        assertThat(first.getTime(), equalTo(tx.getTime()));
        assertThat(first.getSum().get().doubleValue(), equalTo(1D));
        assertThat(first.getMax().get().doubleValue(), equalTo(1D));
        assertThat(first.getMin().get().doubleValue(), equalTo(1D));
        assertThat(first.getAvg().doubleValue(), equalTo(1D));
        assertThat(first.getCount().get(), equalTo(1L));

        StatValue second = service.add(new StatTx(theTime - 1, 2D));
        StatValue stats = service.getStats();
        assertThat(stats.getSum().get().doubleValue(), equalTo(3D));
        assertThat(stats.getCount().get(), equalTo(2L));
        assertThat(stats.getAvg().doubleValue(), equalTo(1.5D));
        assertThat(service.map.size(), equalTo(2));
    }

    @Test @SneakyThrows public void testSubtract(){
        final StatsServiceImpl service = new StatsServiceImpl();
        long theTime = currentTimeMillis();
        StatTx tx = new StatTx(theTime, 1D);
        StatValue first = service.add(tx);

        service.add(new StatTx(new DateTime().minusSeconds(59).getMillis(), 1D));

        StatValue stats = service.getStats();

        assertThat(stats.getSum().get(), equalTo(new BigDecimal(2)));



        Thread.sleep(1001);

        stats = service.getStats();

        assertThat(stats.getSum().get(), equalTo(new BigDecimal(1)));
    }

    @Test
    public void withinOneMinute() {
        final StatsServiceImpl service = new StatsServiceImpl();
        final long theTime = currentTimeMillis();
        final int count = MINUTE+1000;
        IntStream.range(1, count).boxed().parallel().forEach(integer -> service.add(new StatTx(theTime - integer / 10, integer.doubleValue())));

        assertThat(service.map.size(), lessThan(MINUTE));
    }

    @Test @SneakyThrows public void overOneMinute(){
        final StatsServiceImpl service = new StatsServiceImpl();
        service.add(new StatTx(currentTimeMillis(), 7D));
        service.add(new StatTx(currentTimeMillis()-MINUTE+1, 7D));

        final StatValue stats1 = service.getStats();

        Thread.sleep(2);

        service.add(new StatTx(currentTimeMillis() - MINUTE / 2, 3D));

        final StatValue stats2 = service.getStats();

        assertThat(stats2.getSum().get(), equalTo(new BigDecimal(10)));
    }

    @Test @SneakyThrows public void testNull(){
        final StatsServiceImpl service = new StatsServiceImpl();
        service.add(new StatTx(currentTimeMillis()-MINUTE+1, 7D));
        Thread.sleep(2);
        assertThat(service.add(new StatTx(0L, 0D)), nullValue());
        assertThat(service.getStats().getSum().get(), equalTo(BigDecimal.ZERO));

    }



    @Test
    public void capacityAndTimeLimitTest() {
        final int count = 1_000_000;
        final StatsServiceImpl service = new StatsServiceImpl();
        final long theTime = currentTimeMillis();
        IntStream.range(1, count).boxed().parallel().forEach(ms -> service.map.put(theTime - ms, new StatValue(theTime - ms, 1D)));

        assertThat(service.map.size(), equalTo(count - 1));

        service.map.clear();

        final int limit = 61_000;
        IntStream.range(1, limit).boxed().forEach(integer -> service.add(new StatTx(theTime - integer / 10, integer.doubleValue())));

        assertThat(service.map.size(), lessThan(MINUTE));
    }

    @Test
    @SneakyThrows
    /*Adds 50K elements in a random order in parallel*/
    public void addAndExpireTest() {
        final StatsServiceImpl service = new StatsServiceImpl();
        final AtomicReference<BigDecimal> sum = new AtomicReference<>(new BigDecimal(0));
        final AtomicInteger statsAdded = new AtomicInteger();
        final AtomicReference<BigDecimal> testMax = new AtomicReference<>(new BigDecimal(Integer.MIN_VALUE));
        final AtomicReference<BigDecimal> testMin = new AtomicReference<>(new BigDecimal(Integer.MAX_VALUE));
        final long theTime = currentTimeMillis();
        final Random random = new Random();
        /*millis to the past*/
        int expire = 3000;
        final int timeCount = MINUTE- expire;
        List<Integer> millis = IntStream.range(1, timeCount).boxed().collect(Collectors.toList());
        millis.parallelStream().forEachOrdered(
                time -> {
                        final double value = random.nextInt(100)+1;
                        service.add(new StatTx(theTime - time, value));
                        sum.set(new BigDecimal(value).add(sum.get()));
                        statsAdded.incrementAndGet();
                        testMax.set(max(testMax.get(), new BigDecimal(value)));
                        testMin.set(min(testMin.get(), new BigDecimal(value)));
                }
        );
        assertThat(statsAdded.intValue(), equalTo(service.map.size()));
        final StatValue stats = service.getStats();
        assertThat(stats.getSum().get(), equalTo(sum.get()));
        assertThat(stats.getCount().get(), equalTo((long) (timeCount - 1)));
        assertThat(stats.getMax().get(), equalTo(testMax.get()));
        assertThat(stats.getMin().get(), equalTo(testMin.get()));
        assertThat(stats.getAvg(), equalTo(sum.get().divide(new BigDecimal(timeCount), 2, HALF_UP)));

        while (currentTimeMillis() - MINUTE < service.map.firstEntry().getValue().getTime()) {
            System.out.println("Waiting for head expiration, StatsServicesec... " + ((currentTimeMillis() - MINUTE) - service.map.firstEntry().getValue().getTime())/1000);
            Thread.sleep(1000);
        }

        StatValue statsExipred = service.getStats();

        assertThat(statsExipred.getSum().get(), lessThan(sum.get()));
        assertThat(statsExipred.getCount().get(), lessThan((long)millis.size()));

    }

    @Test @SneakyThrows public void testEmptyAndExpired(){
        final StatsServiceImpl service = new StatsServiceImpl();
        StatValue stats = service.getStats();
        assertThat(stats.getSum().get().doubleValue(), equalTo(0D));

        assertThat(service.add(new StatTx(currentTimeMillis()-(MINUTE+1000), 0D)), nullValue());
        assertThat(service.getStats().getCount().get(), equalTo(0L));

        assertThat(service.add(new StatTx(currentTimeMillis()-(MINUTE - 999), 0D)), notNullValue());

        Thread.sleep(1000);

        assertThat(service.getStats().getCount().get(), equalTo(0L));

        assertThat(service.add(new StatTx(currentTimeMillis()-MINUTE + 10, 0D)), notNullValue());

        assertThat(service.getStats().getCount().get(), equalTo(1L));

        Thread.sleep(1000);

        assertThat(service.add(new StatTx(currentTimeMillis() - MINUTE - 10, 0D)), nullValue());
    }

    @Test @SneakyThrows public void testHeadOlderThanMinute(){
        final StatsServiceImpl service = new StatsServiceImpl();
        service.add(new StatTx(currentTimeMillis() - MINUTE +1, 5D));
        Thread.sleep(2);
        assertThat(service.add(new StatTx(currentTimeMillis(), 3D)).getSum().get(), equalTo(new BigDecimal(3)));

    }
}
