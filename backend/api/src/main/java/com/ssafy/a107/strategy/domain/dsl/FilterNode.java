package com.ssafy.a107.strategy.domain.dsl;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GroupFilterNode.class, name = "GROUP"),
        @JsonSubTypes.Type(value = ConditionFilterNode.class, name = "CONDITION")
})
public sealed interface FilterNode permits GroupFilterNode, ConditionFilterNode {
}
