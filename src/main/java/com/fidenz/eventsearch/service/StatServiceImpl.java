package com.fidenz.eventsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidenz.eventsearch.dto.*;
import com.fidenz.eventsearch.entity.EventDetail;
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
import org.elasticsearch.search.sort.ScoreSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.joda.time.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StatServiceImpl implements StatServiceInterface {
    @Value("${spring.data.elasticsearch.pagination-size}")
    private int pagination_size;

    @Autowired
    private ObjectMapper objectMapper;

    @Qualifier("createInstance")
    @Autowired
    private RestHighLevelClient client;


    @Override
    public GenericCounter findCounter(List<Filter> filters, TimeRange timeRange) throws IOException {
        TermsAggregationBuilder aggregationBuilderAggName = AggregationBuilders.terms("agg_names").field("Agg.Name.keyword").size(100000000).minDocCount(1);
        CardinalityAggregationBuilder aggregationBuildEvent = AggregationBuilders.cardinality("events").field("id");
        TermsAggregationBuilder aggregationBuilderMotionDetector = AggregationBuilders.terms("motion_detectors").field("Event.Params.DeviceName.keyword").size(100000000).minDocCount(1);
        TermsAggregationBuilder aggregationBuilderCamera = AggregationBuilders.terms("cameras").field("Event.Params.DeviceName.keyword").size(100000000).minDocCount(1);

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices("event_detail");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder searchQuery = QueryBuilders.boolQuery();

        searchQuery.filter(QueryBuilders.rangeQuery("Timestamp").gte(timeRange.getFrom()).lte(timeRange.getTo()));
        prepareFilters(searchQuery, filters);

        searchSourceBuilder.query(searchQuery).aggregation(aggregationBuilderAggName)
                .aggregation(aggregationBuildEvent)
                .aggregation(aggregationBuilderMotionDetector)
                .aggregation(aggregationBuilderCamera);

        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        Terms agg_name = searchResponse.getAggregations().get("agg_names");
        Terms motion_detector = searchResponse.getAggregations().get("motion_detectors");
        Terms camera = searchResponse.getAggregations().get("cameras");
        Cardinality event = searchResponse.getAggregations().get("events");

        GenericCounter genericCounter = new GenericCounter();
        genericCounter.setNoOfCameras(camera.getBuckets().size());
        genericCounter.setNoOfEvents(event.getValue());
        genericCounter.setNoOfLocations(agg_name.getBuckets().size());
        genericCounter.setNoOfMotionDetectors(motion_detector.getBuckets().size());

        return genericCounter;
    }

    @Override
    public List<String> findCameras(List<Filter> filters, TimeRange timeRange) throws IOException {
        TermsAggregationBuilder aggregationBuilderCamera = AggregationBuilders.terms("cameras").field("Event.Params.DeviceName.keyword").size(100000000).minDocCount(1);

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices("event_detail");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder searchQuery = QueryBuilders.boolQuery();

        searchQuery.filter(QueryBuilders.rangeQuery("Timestamp").gte(timeRange.getFrom()).lte(timeRange.getTo()));
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
    }

    @Override
    public AverageCounter findAverages(List<Filter> filters) throws IOException {
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
        DateTime first_event_time = new DateTime();
        for (SearchHit hit : searchHitFirst) {
            first_event_time = objectMapper.convertValue(hit.getSourceAsMap(), EventDetail.class).getTimestamp();
        }

        SearchSourceBuilder searchSourceBuilderLast = new SearchSourceBuilder();
        searchSourceBuilderLast.query(searchQuery).size(1).sort(new FieldSortBuilder("Timestamp").order(SortOrder.DESC)).size(1).fetchSource(new String[]{"Timestamp"}, null);
        searchRequest.source(searchSourceBuilderLast);
        SearchResponse searchResponseLast = client.search(searchRequest, RequestOptions.DEFAULT);
        SearchHit[] searchHitLast = searchResponseLast.getHits().getHits();
        DateTime last_event_time = new DateTime();
        for (SearchHit hit : searchHitLast) {
            last_event_time = objectMapper.convertValue(hit.getSourceAsMap(), EventDetail.class).getTimestamp();
        }

        int weeks = Weeks.weeksBetween(first_event_time.withTimeAtStartOfDay(), last_event_time).getWeeks() + 1;
        int days = Days.daysBetween(first_event_time, last_event_time).getDays() + 1;
        int hours = Hours.hoursBetween(first_event_time, last_event_time).getHours() + 1;
        long total_hits =  countResponse.getCount();

        AverageCounter averageCounter = new AverageCounter();
        averageCounter.setAvgForWeek(total_hits/weeks);
        averageCounter.setAvgForDay(total_hits/days);
        averageCounter.setAvgForHour(total_hits/hours);

        return averageCounter;
    }

    @Override
    public HashMap<String, Long> findCountByCategory(List<Filter> filters, TimeRange timeRange) throws IOException {
        TermsAggregationBuilder aggregationBuilderCamera = AggregationBuilders.terms("cameras").field("Event.Params.DeviceName.keyword").size(100000000).minDocCount(1);

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices("event_detail");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder searchQuery = QueryBuilders.boolQuery();

        searchQuery.filter(QueryBuilders.rangeQuery("Timestamp").gte(timeRange.getFrom()).lte(timeRange.getTo()));
        prepareFilters(searchQuery, filters);

        searchSourceBuilder.query(searchQuery).aggregation(aggregationBuilderCamera);

        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        Terms camera = searchResponse.getAggregations().get("cameras");

        HashMap<String, Long> cameraListCount = new HashMap<>();

        for (final Terms.Bucket entry : camera.getBuckets()) {
            cameraListCount.put(entry.getKeyAsString(), entry.getDocCount());
        }

        return cameraListCount;
    }

    @Override
    public EventTimeRange findEventTimeRange(String event_start, String event_end, List<Filter> filters, TimeRange timeRange) throws IOException {
        MultiSearchRequest request = new MultiSearchRequest();
        SearchRequest firstSearchRequest = new SearchRequest();
        BoolQueryBuilder searchQuery = QueryBuilders.boolQuery();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchQuery("Event.Params.Name", event_start).operator(Operator.AND));
        searchSourceBuilder.sort(new FieldSortBuilder("Timestamp").order(SortOrder.DESC));
        searchSourceBuilder.size(1);
        prepareFilters(searchQuery, filters);
        firstSearchRequest.source(searchSourceBuilder);
        request.add(firstSearchRequest);
        SearchRequest secondSearchRequest = new SearchRequest();
        searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchQuery("Event.Params.Name", event_end).operator(Operator.AND));
        searchSourceBuilder.sort(new FieldSortBuilder("Timestamp").order(SortOrder.DESC));
        searchSourceBuilder.size(1);
        prepareFilters(searchQuery, filters);
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

        System.out.println(eventDetailFirst);
        System.out.println(eventDetailSecond);

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
    }

    private void prepareFilters(BoolQueryBuilder searchQuery, List<Filter> filters) {
        if (filters == null) {
            return;
        }
        filters.stream().collect(Collectors.groupingBy(Filter::getKey)).forEach((key, values) -> {
            BoolQueryBuilder bool = QueryBuilders.boolQuery();
            values.forEach(value -> bool.should(QueryBuilders.matchQuery(key, value.getValue())));
            searchQuery.must(bool);
        });
    }
}
