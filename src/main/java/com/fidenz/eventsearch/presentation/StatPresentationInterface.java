package com.fidenz.eventsearch.presentation;

import com.fidenz.eventsearch.dto.*;

import java.io.IOException;
import java.util.List;

public interface StatPresentationInterface {
    AverageCounterDTO getAverages(List<FilterDTO> filters) throws IOException;
    GenericCounterDTO getCounter(List<FilterDTO> filters, TimeRangeDTO timeRangeDTO) throws IOException;
    List<String> getCameras(List<FilterDTO> filters, TimeRangeDTO timeRangeDTO) throws IOException;
    EventByCategoryDTO getCountByCategory(List<FilterDTO> filters, TimeRangeDTO timeRangeDTO) throws IOException;
    EventTimeRangeDTO getEventTimeRange(String event_start, String event_end, List<FilterDTO> filters, TimeRangeDTO timeRangeDTO) throws IOException;
}
