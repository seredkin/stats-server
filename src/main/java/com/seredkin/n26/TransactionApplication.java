package com.seredkin.n26;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@SpringBootApplication
@RestController
public class TransactionApplication {

    @Autowired private StatsService statsService;

    public static void main(String[] args) {
        SpringApplication.run(TransactionApplication.class, args);
    }

    @RequestMapping(path = "/transactions", method = POST)
    ResponseEntity addMeasure(@RequestBody StatTx tx) {
        return statsService.add(tx) !=null?new ResponseEntity<>(HttpStatus.CREATED):new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @RequestMapping(path = "/statistics", method = GET)
    @ResponseBody
    StatValue sensorStats() {
        return statsService.getStats();
    }
}
