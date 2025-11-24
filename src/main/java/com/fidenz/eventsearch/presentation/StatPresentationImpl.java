package com.fidenz.eventsearch.presentation;

import com.fidenz.eventsearch.dto.*;
import com.fidenz.eventsearch.mapper.AverageCounterMapper;
import com.fidenz.eventsearch.mapper.EventByCategoryMapper;
import com.fidenz.eventsearch.mapper.EventTimeRangeMapper;
import com.fidenz.eventsearch.mapper.GenericCounterMapper;
import com.fidenz.eventsearch.service.StatServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class StatPresentationImpl implements StatPresentationInterface{

    @Autowired
    private StatServiceInterface statService;

    public AverageCounterDTO getAverages(List<FilterDTO> filters) throws IOException {
        return AverageCounterMapper.INSTANCE.averageCounterToAverageCounterDTO(this.statService.findAverages(filters));
    }

    @Override
    public GenericCounterDTO getCounter(List<FilterDTO> filters, TimeRangeDTO timeRangeDTO) throws IOException {
        return GenericCounterMapper.INSTANCE.genericCounterToGenericCounterDTO(this.statService.findCounter(filters, timeRangeDTO));
    }

    @Override
    public List<String> getCameras(List<FilterDTO> filters, TimeRangeDTO timeRangeDTO) throws IOException {
        return this.statService.findCameras(filters, timeRangeDTO);
    }

    @Override
    public EventByCategoryDTO getCountByCategory(List<FilterDTO> filters, TimeRangeDTO timeRangeDTO) throws IOException {
        return EventByCategoryMapper.INSTANCE.eventByCategoryToEEventByCategoryDTO(this.statService.findCountByCategory(filters, timeRangeDTO));
    }

    @Override
    public EventTimeRangeDTO getEventTimeRange(String eventStart, String eventEnd, List<FilterDTO> filters, TimeRangeDTO timeRangeDTO) throws IOException {
        return EventTimeRangeMapper.INSTANCE.eventTimeRangeToEventTimeRangeDTO(this.statService.findEventTimeRange(eventStart, eventEnd, filters, timeRangeDTO));
    }
}
