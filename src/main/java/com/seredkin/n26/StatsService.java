package com.seredkin.n26;

public interface StatsService {
    int MINUTE = 60_000;

    StatValue add(StatTx tx);

    StatValue getStats();
}
