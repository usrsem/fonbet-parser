package org.parser.fonbetparser.service;

import org.parser.fonbetparser.domain.LiveLine;

public interface FonbetLiveParserService {
    void deserialize();
    LiveLine getTargetSportEvents(String sportName);
}
