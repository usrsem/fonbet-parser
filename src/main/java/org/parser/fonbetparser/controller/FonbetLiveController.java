package org.parser.fonbetparser.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.parser.fonbetparser.domain.SportEvent;
import org.parser.fonbetparser.service.FonbetLiveParserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Set;

@Controller
@AllArgsConstructor
@Slf4j
public class FonbetLiveController {

    private final FonbetLiveParserService parserService;

    @GetMapping("/")
    @ResponseBody
    public ResponseEntity<Set<SportEvent>> getSportEvents(@RequestParam(name = "sportName") String sportName) {
        return new ResponseEntity<>(parserService.getSportEvents(sportName), HttpStatus.OK);
    }
}
