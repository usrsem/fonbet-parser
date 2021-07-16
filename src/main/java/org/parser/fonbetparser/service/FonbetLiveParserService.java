package org.parser.fonbetparser.service;

import org.parser.fonbetparser.domain.SportEvent;

import java.util.Set;

public interface FonbetLiveParserService {
    void deserialize();
    Set<SportEvent> getSportEvents(String sportName);
}
