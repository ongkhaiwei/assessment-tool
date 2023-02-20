package org.veritas.assessment.biz.constant;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

public enum PlotTypeEnum {
    NONE("none", "Not need to transform as plot."),
    PIE("pie", "Pie"),
    BAR("bar", "Bar"),
    CURVE("curve", "Curve"),
    TWO_LINE("two_line", "Two line"),
    ;

    @JsonValue
    @Getter
    private final String name;
    @Getter
    private final String description;

    PlotTypeEnum(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
