package com.ssafy.a107.strategy.domain.dsl;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FactorOperand.class, name = "FACTOR"),
        @JsonSubTypes.Type(value = ValueOperand.class, name = "VALUE")
})
public sealed interface Operand permits FactorOperand, ValueOperand {
}
