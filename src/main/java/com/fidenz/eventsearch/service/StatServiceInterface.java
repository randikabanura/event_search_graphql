package com.fidenz.eventsearch.service;

import com.fidenz.eventsearch.dto.FilterDTO;
import com.fidenz.eventsearch.dto.TimeRangeDTO;
import com.fidenz.eventsearch.entity.AverageCounter;
import com.fidenz.eventsearch.entity.EventByCategory;
import com.fidenz.eventsearch.entity.EventTimeRange;
import com.fidenz.eventsearch.entity.GenericCounter;

import java.io.IOException;
import java.util.List;

public interface StatServiceInterface {
    GenericCounter findCounter(List<FilterDTO> filters, TimeRangeDTO timeRangeDTO) throws IOException;
    List<String> findCameras(List<FilterDTO> filters, TimeRangeDTO timeRangeDTO) throws IOException;
    AverageCounter findAverages(List<FilterDTO> filters) throws IOException;
    EventByCategory findCountByCategory(List<FilterDTO> filters, TimeRangeDTO timeRangeDTO) throws IOException;
    EventTimeRange findEventTimeRange(String event_start, String event_end, List<FilterDTO> filters, TimeRangeDTO timeRangeDTO) throws IOException;
}
