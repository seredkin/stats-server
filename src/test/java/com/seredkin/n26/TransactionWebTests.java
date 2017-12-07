package com.seredkin.n26;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TransactionWebTests {

    public static final String API_V1_SENSORS = "api/v1/sensors/";
    String prefix = "";
    @LocalServerPort Integer port;

    @Autowired
    WebTestClient webClient;

    @Autowired
    StatsService statService;
    private final Random r = new Random();

    @Test
    public void fetchStats() {

        final DateTime theTime = new DateTime();
        final UUID uuid = UUID.randomUUID();
        final Random r  = new Random();
        StatValue emptyStats = getStats();
        assertThat(emptyStats.getSum().get().doubleValue(), equalTo(0D));
        assertThat(emptyStats.getCount().get(), equalTo(0L));

        webClient.post().uri("/transactions")
                .syncBody(new StatTx(System.currentTimeMillis(), 1D))
                .exchange().expectStatus().isCreated();

        StatValue realStats = getStats();

        assertThat(realStats.getSum().get(), equalTo(new BigDecimal(1)));

        webClient.post().uri("/transactions")
                .syncBody(new StatTx(0L, 1D))
                .exchange().expectStatus().isNoContent();


    }

    private StatValue getStats() {
        return webClient.get().uri("/statistics")
                .exchange().expectBody(StatValue.class).returnResult().getResponseBody();
    }
}
