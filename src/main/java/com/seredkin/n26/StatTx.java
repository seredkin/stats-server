package com.seredkin.n26;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class StatTx {
    @Getter
    final Long time;
    @Getter
    final Double value;
}
