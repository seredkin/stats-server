package com.seredkin.n26;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.System.currentTimeMillis;

@Service
class StatsServiceImpl implements StatsService {

    final ConcurrentSkipListMap<Long, StatValue> map = new ConcurrentSkipListMap<>();
    private ExecutorService executorService = Executors.newWorkStealingPool();

    @Override
    public StatValue add(final StatTx tx) {
        if (!withinLastMinute(tx.getTime())) {
            if (!map.isEmpty() && !withinLastMinute(map.lastKey()))
                map.clear();
            return null;
        }

        final StatValue fresh = new StatValue(tx.getTime(), tx.getValue());

        if (map.isEmpty()) {
            return mergeMap(tx, fresh);
        } else if (!withinLastMinute(map.lastKey())) {
            map.clear();
            return mergeMap(tx, fresh);
        } else if (!withinLastMinute(map.firstKey())) {
            fresh.subtract(map.floorEntry(tx.getTime() - 1).getValue());
            map.lastEntry().getValue().sumWith(fresh);
            map.entrySet().removeIf(longStatValueEntry -> !withinLastMinute(longStatValueEntry.getKey()));
            return map.lastEntry().getValue();
        } else if (map.firstKey() > tx.getTime()) {
            map.lastEntry().getValue().sumWith(fresh);
            mergeMap(tx, fresh);
            return fresh;
        } else/* if (withinLastMinute(map.firstKey())) */{
            mergeMap(tx, fresh);
            return map.lastEntry().getValue().sumWith(fresh);
        }
    }

    private StatValue mergeMap(StatTx tx, StatValue fresh) {
        return map.merge(tx.getTime(), fresh, StatValue::sumWith);
    }

    @Override
    public StatValue getStats() {
        final long now = currentTimeMillis();

        if (map.isEmpty())
            return StatValue.empty(now);
        else if(withinLastMinute(map.lastKey()) && withinLastMinute(map.firstKey()))
            return map.lastEntry().getValue();
        else if (!withinLastMinute(map.lastKey())) {
            map.clear();
            return StatValue.empty(now);
        } else /*if (!withinLastMinute(map.firstKey()))*/{
            StatValue lastToExpire = map.floorEntry(now - MINUTE).getValue();
            executorService.submit(() -> map.entrySet().removeIf(longStatValueEntry -> !withinLastMinute(longStatValueEntry.getKey())));
            return map.lastEntry().getValue().subtract(lastToExpire);
        }
    }

    private boolean withinLastMinute(Long tx) {
        return System.currentTimeMillis() - tx < 60_000;
    }
}
