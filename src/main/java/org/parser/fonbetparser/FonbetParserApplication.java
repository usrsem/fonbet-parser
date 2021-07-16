package org.parser.fonbetparser;

import org.parser.fonbetparser.service.FonbetLiveParserService;
import org.parser.fonbetparser.service.FonbetLiveParserServiceImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FonbetParserApplication {

    public static void main(String[] args) {
        SpringApplication.run(FonbetParserApplication.class, args);
    }

}
