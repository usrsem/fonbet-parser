package org.parser.fonbetparser.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.parser.fonbetparser.domain.LiveLine;
import org.parser.fonbetparser.service.FonbetLiveParserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
@AllArgsConstructor
@Slf4j
public class FonbetLiveController {

    private final FonbetLiveParserService parserService;

    @GetMapping(value = "/")
    public String getMainPage() {
        return "index";
    }

    @GetMapping("/live-sport-events")
    @ResponseBody
    public ResponseEntity<LiveLine> getSportEvents(@RequestParam(name = "sportName") String sportName) {
        return new ResponseEntity<>(parserService.getTargetSportEvents(sportName), HttpStatus.OK);
    }
}
