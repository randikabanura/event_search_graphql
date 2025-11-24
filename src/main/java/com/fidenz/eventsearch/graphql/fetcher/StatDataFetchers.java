package com.fidenz.eventsearch.graphql.fetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidenz.eventsearch.dto.FilterDTO;
import com.fidenz.eventsearch.dto.TimeRangeDTO;
import com.fidenz.eventsearch.entity.*;
import graphql.schema.DataFetcher;
import org.apache.tomcat.util.json.JSONParser;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.Cardinality;
import org.elasticsearch.search.aggregations.metrics.CardinalityAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.joda.time.*;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class StatDataFetchers {

    @Autowired
    private RestHighLevelClient client;

    @Autowired
    private ObjectMapper objectMapper;

    public DataFetcher getCounter(){
        return dataFetchingEnvironment -> {
            HashMap timeRangeString= dataFetchingEnvironment.getArgument("timeRange");
            TimeRangeDTO timeRangeDTO = new ObjectMapper().readValue(objectMapper.writeValueAsString(timeRangeString), TimeRangeDTO.class);

            List<FilterDTO> filtersStringList = dataFetchingEnvironment.getArgument("filters");
            List<FilterDTO> filters = new ArrayList<>();

            if(filtersStringList != null) {
                for (int i = 0; i < filtersStringList.size(); i++) {
                    FilterDTO singleFilter = new ObjectMapper().readValue(objectMapper.writeValueAsString(filtersStringList.get(i)), FilterDTO.class);
                    filters.add(singleFilter);
                }
            }

            TermsAggregationBuilder aggregationBuilderAggName = AggregationBuilders.terms("agg_names").field("Agg.Name.keyword").size(100000000).minDocCount(1);
            CardinalityAggregationBuilder aggregationBuildEvent = AggregationBuilders.cardinality("events").field("id");
            TermsAggregationBuilder aggregationBuilderMotionDetector = AggregationBuilders.terms("motion_detectors").field("Event.Params.DeviceName.keyword").size(100000000).minDocCount(1);
            TermsAggregationBuilder aggregationBuilderCamera = AggregationBuilders.terms("cameras").field("Event.Params.DeviceName.keyword").size(100000000).minDocCount(1);

            SearchRequest searchRequest = new SearchRequest();
            searchRequest.indices("event_detail");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            BoolQueryBuilder searchQuery = QueryBuilders.boolQuery();

            if(timeRangeDTO != null) {
                searchQuery.filter(QueryBuilders.rangeQuery("Timestamp").gte(timeRangeDTO.getFrom()).lte(timeRangeDTO.getTo()));
            }
            prepareFilters(searchQuery, filters);

            searchSourceBuilder.query(searchQuery).aggregation(aggregationBuilderAggName)
                    .aggregation(aggregationBuildEvent)
                    .aggregation(aggregationBuilderMotionDetector)
                    .aggregation(aggregationBuilderCamera);

            searchRequest.source(searchSourceBuilder);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            Terms aggName = searchResponse.getAggregations().get("agg_names");
            Terms motion_detector = searchResponse.getAggregations().get("motion_detectors");
            Terms camera = searchResponse.getAggregations().get("cameras");
            Cardinality event = searchResponse.getAggregations().get("events");

            GenericCounter genericCounter = new GenericCounter();
            genericCounter.setNoOfCameras(camera.getBuckets().size());
            genericCounter.setNoOfEvents(event.getValue());
            genericCounter.setNoOfLocations(aggName.getBuckets().size());
            genericCounter.setNoOfMotionDetectors(motion_detector.getBuckets().size());

            return genericCounter;
        };
    }

    public DataFetcher getAverages(){
        return dataFetchingEnvironment -> {
            List<FilterDTO> filtersStringList = dataFetchingEnvironment.getArgument("filters");
            List<FilterDTO> filters = new ArrayList<>();

            if(filtersStringList != null) {
                for (int i = 0; i < filtersStringList.size(); i++) {
                    FilterDTO singleFilter = new ObjectMapper().readValue(objectMapper.writeValueAsString(filtersStringList.get(i)), FilterDTO.class);
                    filters.add(singleFilter);
                }
            }

            CountRequest countRequest = new CountRequest();
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            BoolQueryBuilder searchQuery = QueryBuilders.boolQuery();
            prepareFilters(searchQuery, filters);
            searchSourceBuilder.query(searchQuery).trackTotalHits(true);
            countRequest.query(searchQuery);
            CountResponse countResponse = client.count(countRequest, RequestOptions.DEFAULT);

            SearchRequest searchRequest = new SearchRequest();
            searchRequest.indices("event_detail");

            SearchSourceBuilder searchSourceBuilderFirst = new SearchSourceBuilder();
            searchSourceBuilderFirst.query(searchQuery).size(1).sort(new FieldSortBuilder("Timestamp").order(SortOrder.ASC)).size(1).fetchSource(new String[]{"Timestamp"}, null);
            searchRequest.source(searchSourceBuilderFirst);
            SearchResponse searchResponseFirst = client.search(searchRequest, RequestOptions.DEFAULT);
            SearchHit[] searchHitFirst = searchResponseFirst.getHits().getHits();
            DateTime firstEventTime = new DateTime();
            for (SearchHit hit : searchHitFirst) {
                firstEventTime = objectMapper.convertValue(hit.getSourceAsMap(), EventDetail.class).getTimestamp();
            }

            SearchSourceBuilder searchSourceBuilderLast = new SearchSourceBuilder();
            searchSourceBuilderLast.query(searchQuery).size(1).sort(new FieldSortBuilder("Timestamp").order(SortOrder.DESC)).size(1).fetchSource(new String[]{"Timestamp"}, null);
            searchRequest.source(searchSourceBuilderLast);
            SearchResponse searchResponseLast = client.search(searchRequest, RequestOptions.DEFAULT);
            SearchHit[] searchHitLast = searchResponseLast.getHits().getHits();
            DateTime lastEventTime = new DateTime();
            for (SearchHit hit : searchHitLast) {
                lastEventTime = objectMapper.convertValue(hit.getSourceAsMap(), EventDetail.class).getTimestamp();
            }

            int weeks = Weeks.weeksBetween(firstEventTime.withTimeAtStartOfDay(), lastEventTime).getWeeks() + 1;
            int days = Days.daysBetween(firstEventTime, lastEventTime).getDays() + 1;
            int hours = Hours.hoursBetween(firstEventTime, lastEventTime).getHours() + 1;
            long total_hits =  countResponse.getCount();

            AverageCounter averageCounter = new AverageCounter();
            averageCounter.setAvgForWeek((float) total_hits/weeks);
            averageCounter.setAvgForDay((float) total_hits/days);
            averageCounter.setAvgForHour((float) total_hits/hours);

            return averageCounter;
        };
    }

    public DataFetcher getCameras(){
        return dataFetchingEnvironment -> {
            HashMap timeRangeString= dataFetchingEnvironment.getArgument("timeRange");
            TimeRangeDTO timeRangeDTO = new ObjectMapper().readValue(objectMapper.writeValueAsString(timeRangeString), TimeRangeDTO.class);

            List<FilterDTO> filtersStringList = dataFetchingEnvironment.getArgument("filters");
            List<FilterDTO> filters = new ArrayList<>();

            if(filtersStringList != null) {
                for (int i = 0; i < filtersStringList.size(); i++) {
                    FilterDTO singleFilter = new ObjectMapper().readValue(objectMapper.writeValueAsString(filtersStringList.get(i)), FilterDTO.class);
                    filters.add(singleFilter);
                }
            }

            TermsAggregationBuilder aggregationBuilderCamera = AggregationBuilders.terms("cameras").field("Event.Params.DeviceName.keyword").size(100000000).minDocCount(1);

            SearchRequest searchRequest = new SearchRequest();
            searchRequest.indices("event_detail");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            BoolQueryBuilder searchQuery = QueryBuilders.boolQuery();
            if(timeRangeDTO != null) {
                searchQuery.filter(QueryBuilders.rangeQuery("Timestamp").gte(timeRangeDTO.getFrom()).lte(timeRangeDTO.getTo()));
            }
            prepareFilters(searchQuery, filters);

            searchSourceBuilder.query(searchQuery).aggregation(aggregationBuilderCamera);

            searchRequest.source(searchSourceBuilder);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            Terms camera = searchResponse.getAggregations().get("cameras");

            List<String> cameraList = new ArrayList<>();

            for (final Terms.Bucket entry : camera.getBuckets()) {
                cameraList.add(entry.getKeyAsString());
            }
            return cameraList;
        };
    }

    public DataFetcher getEventTimeRange(){
        return dataFetchingEnvironment -> {
            HashMap timeRangeString= dataFetchingEnvironment.getArgument("timeRange");
            TimeRangeDTO timeRangeDTO = new ObjectMapper().readValue(objectMapper.writeValueAsString(timeRangeString), TimeRangeDTO.class);

            List<FilterDTO> filtersStringList = dataFetchingEnvironment.getArgument("filters");
            List<FilterDTO> filters = new ArrayList<>();

            if(filtersStringList != null) {
                for (int i = 0; i < filtersStringList.size(); i++) {
                    FilterDTO singleFilter = new ObjectMapper().readValue(objectMapper.writeValueAsString(filtersStringList.get(i)), FilterDTO.class);
                    filters.add(singleFilter);
                }
            }

            String eventStart = dataFetchingEnvironment.getArgument("eventStart");
            String eventEnd = dataFetchingEnvironment.getArgument("eventEnd");

            MultiSearchRequest request = new MultiSearchRequest();
            SearchRequest firstSearchRequest = new SearchRequest();
            BoolQueryBuilder searchQueryFirst = QueryBuilders.boolQuery();
            if(timeRangeDTO != null) {
                searchQueryFirst.filter(QueryBuilders.rangeQuery("Timestamp").gte(timeRangeDTO.getFrom()).lte(timeRangeDTO.getTo()));
            }
            searchQueryFirst.must(QueryBuilders.matchQuery("Event.Params.Name", eventStart).operator(Operator.AND));
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            prepareFilters(searchQueryFirst, filters);
            searchSourceBuilder.query(searchQueryFirst);
            searchSourceBuilder.sort(new FieldSortBuilder("Timestamp").order(SortOrder.DESC));
            searchSourceBuilder.size(1);
            firstSearchRequest.source(searchSourceBuilder);
            request.add(firstSearchRequest);
            SearchRequest secondSearchRequest = new SearchRequest();
            BoolQueryBuilder searchQuerySecond = QueryBuilders.boolQuery();
            if(timeRangeDTO != null) {
                searchQuerySecond.filter(QueryBuilders.rangeQuery("Timestamp").gte(timeRangeDTO.getFrom()).lte(timeRangeDTO.getTo()));
            }
            searchQuerySecond.must(QueryBuilders.matchQuery("Event.Params.Name", eventEnd).operator(Operator.AND));
            prepareFilters(searchQuerySecond, filters);
            searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(searchQuerySecond);
            searchSourceBuilder.sort(new FieldSortBuilder("Timestamp").order(SortOrder.DESC));
            searchSourceBuilder.size(1);
            secondSearchRequest.source(searchSourceBuilder);
            request.add(secondSearchRequest);

            MultiSearchResponse multiSearchResponse = client.msearch(request, RequestOptions.DEFAULT);

            MultiSearchResponse.Item firstResponse = multiSearchResponse.getResponses()[0];
            SearchResponse searchResponseFirst = firstResponse.getResponse();
            MultiSearchResponse.Item secondResponse = multiSearchResponse.getResponses()[1];
            SearchResponse searchResponseSecond = secondResponse.getResponse();


            SearchHit[] searchHitSecond = searchResponseSecond.getHits().getHits();
            EventDetail eventDetailSecond = new EventDetail();
            for (SearchHit hit : searchHitSecond) {
                eventDetailSecond = objectMapper.convertValue(hit.getSourceAsMap(), EventDetail.class);
            }

            SearchHit[] searchHitFirst = searchResponseFirst.getHits().getHits();
            EventDetail eventDetailFirst = new EventDetail();
            for (SearchHit hit : searchHitFirst) {
                eventDetailFirst = objectMapper.convertValue(hit.getSourceAsMap(), EventDetail.class);
            }


            if(searchHitFirst.length == 1 && searchHitSecond.length == 1) {
                EventTimeRange eventTimeRange = new EventTimeRange();
                eventTimeRange.setEndEvent(eventDetailSecond);
                eventTimeRange.setStartEvent(eventDetailFirst);
                eventTimeRange.setFrom(eventDetailFirst.getTimestamp());
                eventTimeRange.setTo(eventDetailSecond.getTimestamp());
                Period period = new Period(eventDetailFirst.getTimestamp(), eventDetailSecond.getTimestamp());
                eventTimeRange.setRange(period.toStandardDuration().getMillis());
                return eventTimeRange;
            }else{
                return null;
            }
        };
    }

    public DataFetcher getEventByCategory(){
        return dataFetchingEnvironment -> {
            HashMap timeRangeString= dataFetchingEnvironment.getArgument("timeRange");
            TimeRangeDTO timeRangeDTO = new ObjectMapper().readValue(objectMapper.writeValueAsString(timeRangeString), TimeRangeDTO.class);

            List<FilterDTO> filtersStringList = dataFetchingEnvironment.getArgument("filters");
            List<FilterDTO> filters = new ArrayList<>();

            if(filtersStringList != null) {
                for (int i = 0; i < filtersStringList.size(); i++) {
                    FilterDTO singleFilter = new ObjectMapper().readValue(objectMapper.writeValueAsString(filtersStringList.get(i)), FilterDTO.class);
                    filters.add(singleFilter);
                }
            }

            TermsAggregationBuilder aggregationBuilderCamera = AggregationBuilders.terms("cameras").field("Event.Params.DeviceName.keyword").size(100000000).minDocCount(1);

            SearchRequest searchRequest = new SearchRequest();
            searchRequest.indices("event_detail");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            BoolQueryBuilder searchQuery = QueryBuilders.boolQuery();

            if(timeRangeDTO != null) {
                searchQuery.filter(QueryBuilders.rangeQuery("Timestamp").gte(timeRangeDTO.getFrom()).lte(timeRangeDTO.getTo()));
            }
            prepareFilters(searchQuery, filters);

            searchSourceBuilder.query(searchQuery).aggregation(aggregationBuilderCamera);

            searchRequest.source(searchSourceBuilder);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            Terms camera = searchResponse.getAggregations().get("cameras");

            EventByCategory cameraListCount = new EventByCategory();
            List<EventCounter> eventCounterList = new ArrayList<EventCounter>();

            for (final Terms.Bucket entry : camera.getBuckets()) {
                EventCounter eventCounter = new EventCounter();
                eventCounter.setCameraName(entry.getKeyAsString());
                eventCounter.setCount(entry.getDocCount());
                eventCounterList.add(eventCounter);
            }

            cameraListCount.setEventCounters(eventCounterList);
            return cameraListCount;
        };
    }

    private void prepareFilters(BoolQueryBuilder searchQuery, List<FilterDTO> filters) {
        if (filters == null) {
            return;
        }

        filters.stream().collect(Collectors.groupingBy(FilterDTO::getKey)).forEach((key, values) -> {
            BoolQueryBuilder bool = QueryBuilders.boolQuery();
            values.forEach(value -> bool.should(QueryBuilders.matchQuery(key, value.getValues())));
            searchQuery.must(bool);
        });
    }
}
