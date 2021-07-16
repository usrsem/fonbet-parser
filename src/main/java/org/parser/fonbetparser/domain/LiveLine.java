package org.parser.fonbetparser.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class LiveLine {
    private String bookmaker;
    private LineType lineType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Set<SportEvent> sportEvents;
}
