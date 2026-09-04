package com.ssafy.a107.trading.backtest.domain;

import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TimeframeAttributeConverter implements AttributeConverter<Timeframe, String> {

    @Override
    public String convertToDatabaseColumn(Timeframe timeframe) {
        return timeframe == null ? null : timeframe.getValue();
    }

    @Override
    public Timeframe convertToEntityAttribute(String value) {
        return value == null ? null : Timeframe.fromValue(value);
    }
}
