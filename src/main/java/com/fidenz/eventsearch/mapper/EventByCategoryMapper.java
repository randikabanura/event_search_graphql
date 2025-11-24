package com.fidenz.eventsearch.mapper;

import com.fidenz.eventsearch.dto.EventByCategoryDTO;
import com.fidenz.eventsearch.entity.EventByCategory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface EventByCategoryMapper {
    EventByCategoryMapper INSTANCE = Mappers.getMapper( EventByCategoryMapper.class );

    @Mapping(source = "eventCounters", target = "eventCounters")
    EventByCategoryDTO eventByCategoryToEEventByCategoryDTO(EventByCategory eventByCategory);
}
