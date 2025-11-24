package com.fidenz.eventsearch.mapper;

import com.fidenz.eventsearch.dto.EventCounterDTO;
import com.fidenz.eventsearch.entity.EventCounter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface EventCounterMapper {
    EventCounterMapper INSTANCE = Mappers.getMapper( EventCounterMapper.class );

    @Mapping(source = "cameraName", target = "cameraName")
    EventCounterDTO eventCounterToEventCounterDTO(EventCounter eventCounter);
}
