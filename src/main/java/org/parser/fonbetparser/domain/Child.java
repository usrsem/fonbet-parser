package org.parser.fonbetparser.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Child {
    private String name;
    private Integer id;
    private Integer parentId;
    private Coefficients coefficients;
}
