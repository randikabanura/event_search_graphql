package com.fidenz.eventsearch.controller;

import com.fidenz.eventsearch.dto.*;
import com.fidenz.eventsearch.presentation.SearchPresentationInterface;
import com.fidenz.eventsearch.presentation.StatPresentationInterface;
import com.fidenz.eventsearch.request.*;
import com.fidenz.eventsearch.service.BulkInsertInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@EnableScheduling
public class Controller {

    @Autowired
    public BulkInsertInterface bulkService;

    @Autowired
    public StatPresentationInterface statPresentation;

    @Autowired
    public SearchPresentationInterface searchPresentation;

    @Scheduled(fixedRateString = "${spring.data.elasticsearch.index-update-time}")
    @PostMapping("/ingest")
    public IngestStatusDTO ingest_data() throws IOException, InterruptedException {
        return bulkService.ingestDataCall();
    }

    @GetMapping("/events")
    public Iterable<EventDetailDTO> getAllData(@RequestParam(defaultValue = "0") int page) throws IOException {
        return searchPresentation.findAll(page);
    }

    @GetMapping("/event/{id}")
    public EventDetailDTO getEvent(@PathVariable String id) throws IOException {
        return searchPresentation.findById(id);
    }

    @PostMapping("/search")
    public Iterable<EventDetailDTO> getSearch(@RequestBody SearchEventRequest searchEventRequest) throws IOException {
        return searchPresentation.search(searchEventRequest.getQuery(), searchEventRequest.getPage(), searchEventRequest.getFilters(), searchEventRequest.getTimeRange());
    }

    @GetMapping("/search")
    public Iterable<EventDetailDTO> getSearch(@RequestParam String query, @RequestParam(defaultValue = "0") int page,  @RequestParam(value = "filters", required = false) String filters, @RequestParam(value = "timeRange", required = false) String timeRange) throws IOException {
        return searchPresentation.search(query, page, this.map(filters), this.setTimeRange(timeRange));
    }

    @PostMapping("/counter")
    public GenericCounterDTO getCount(@RequestBody CounterRequest counterRequest) throws IOException {
        return statPresentation.getCounter(counterRequest.getFilters(), counterRequest.getTimeRange());
    }

    @GetMapping("/counter")
    public GenericCounterDTO getCount(@RequestParam(value = "filters", required = false) String filters, @RequestParam(value = "timeRange", required = false) String timeRange) throws IOException {
        return statPresentation.getCounter(this.map(filters), this.setTimeRange(timeRange));
    }

    @PostMapping("/cameras")
    public List<String> getCameras(@RequestBody CameraRequest cameraRequest) throws IOException {
        return statPresentation.getCameras(cameraRequest.getFilters(), cameraRequest.getTimeRange());
    }

    @GetMapping("/cameras")
    public List<String> getCameras(@RequestParam(value = "filters", required = false) String filters, @RequestParam(value = "timeRange", required = false) String timeRange) throws IOException {
        return statPresentation.getCameras(this.map(filters), this.setTimeRange(timeRange));
    }

    @PostMapping("/averages")
    public AverageCounterDTO getAverages(@RequestBody AverageRequest averageRequest) throws IOException {
        return statPresentation.getAverages(averageRequest.getFilters());
    }

    @GetMapping("/averages")
    public AverageCounterDTO getAverages(@RequestParam(value = "filters", required = false) String filters) throws IOException {
        return statPresentation.getAverages(this.map(filters));
    }

    @PostMapping("/events_by_category")
    public EventByCategoryDTO getCountByCategory(@RequestBody CountByCategoryRequest countByCategoryRequest) throws IOException {
        return statPresentation.getCountByCategory(countByCategoryRequest.getFilters(), countByCategoryRequest.getTimeRange());
    }

    @GetMapping("/events_by_category")
    public EventByCategoryDTO getCountByCategory(@RequestParam(value = "filters", required = false) String filters, @RequestParam(value = "timeRange", required = false) String timeRange) throws IOException {
        return statPresentation.getCountByCategory(this.map(filters), this.setTimeRange(timeRange));
    }

    @PostMapping("/event_time")
    public EventTimeRangeDTO getEventTimeRange(@RequestBody EventTimeRangeRequest eventTimeRangeRequest) throws IOException {
        return statPresentation.getEventTimeRange(eventTimeRangeRequest.getEventStart(), eventTimeRangeRequest.getEventEnd(), eventTimeRangeRequest.getFilters(), eventTimeRangeRequest.getTimeRange());
    }

    @GetMapping("/event_time")
    public EventTimeRangeDTO getEventTimeRange(@RequestParam String eventStart, @RequestParam String eventEnd, @RequestParam(value = "filters", required = false) String filters, @RequestParam(value = "timeRange", required = false) String timeRange) throws IOException {
        return statPresentation.getEventTimeRange(eventStart, eventEnd, this.map(filters), this.setTimeRange(timeRange));
    }

    private List<FilterDTO> map(String filters) {
        List<FilterDTO> filterList = new ArrayList<>();
        if (filters == null) {
            return filterList;
        }
        if (filters.contains(";")) {
            for (String filter : filters.split(";")) {
                filterList.addAll(this.mapValues(filter));
            }
        } else {
            filterList.addAll(this.mapValues(filters));
        }
        return filterList.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<FilterDTO> mapValues(String filter) {
        List<FilterDTO> filters = new ArrayList<>();
        // check if filter string is valid
        if (!filter.contains(":")) {
            return filters;
        }
        final String[] values = filter.split(":");
        if (values[1].contains(",")) {
            for (String f : values[1].split(",")) {
                filters.add(new FilterDTO(values[0], f));
            }
        } else {
            filters.add(new FilterDTO(values[0], values[1]));
        }

        return filters;
    }

    private TimeRangeDTO setTimeRange(String time_range){
        TimeRangeDTO timeRangeDTO = new TimeRangeDTO();
        if (time_range == null) {
            return timeRangeDTO;
        }

        for (String time : time_range.split(";")) {
            final String[] values = time.split(":");

            if(values[0].toLowerCase().equals("from")) {
                timeRangeDTO.setFrom(values[1]);
            }
            if(values[0].toLowerCase().equals("to")) {
                timeRangeDTO.setTo(values[1]);
            }
        }

        return timeRangeDTO;
    }
}
