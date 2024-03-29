package com.fidenz.eventsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidenz.eventsearch.dto.FilterDTO;
import com.fidenz.eventsearch.dto.TimeRangeDTO;
import com.fidenz.eventsearch.entity.EventDetail;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchServiceInterface {

    @Autowired
    private RestHighLevelClient client;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.data.elasticsearch.pagination-size}")
    private int pagination_size;


    private  final String[] includedFields = {"Timestamp", "Node", "Agg", "MessageType", "id", "Event.Topic" , "Event.Params.Message", "Event.Params.Message", "Event.Params.Category", "Event.Params.DeviceName", "Event.Params.Name"};

    @Override
    public EventDetail findById(String id) throws IOException{
        GetRequest getRequest = new GetRequest("event_detail", id);

        GetResponse getResponse = client.get(getRequest, RequestOptions.DEFAULT);

        if (getResponse.isExists()) {
            Map<String, Object> sourceAsMap = getResponse.getSourceAsMap();;
            return objectMapper.convertValue(sourceAsMap, EventDetail.class);
        } else {
            return null;
        }
    }

    public List<EventDetail> findAll(int page) throws IOException{
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices("event_detail");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder searchQuery = QueryBuilders.boolQuery();

        searchSourceBuilder.query(searchQuery).from(page * pagination_size).size(pagination_size);
        searchSourceBuilder.fetchSource(includedFields, null);

        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        SearchHit[] searchHit = searchResponse.getHits().getHits();
        List<EventDetail> eventList = new ArrayList<>();
        for (SearchHit hit : searchHit) {
            eventList.add(objectMapper.convertValue(hit.getSourceAsMap(), EventDetail.class));
        }

        return eventList;
    }

    public List<EventDetail> search(String query, int page, List<FilterDTO> filters, TimeRangeDTO timeRangeDTO) throws IOException{
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices("event_detail");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder searchQuery = QueryBuilders.boolQuery();
        if (query != null && !query.isEmpty()) {
            MultiMatchQueryBuilder multiMatchQuery = QueryBuilders.multiMatchQuery(query);
            searchQuery.must(multiMatchQuery);
        }

        searchQuery.filter(QueryBuilders.rangeQuery("Timestamp").gte(timeRangeDTO.getFrom()).lte(timeRangeDTO.getTo()));
        prepareFilters(searchQuery, filters);

        searchSourceBuilder.query(searchQuery).from(page * pagination_size).size(pagination_size);
        searchSourceBuilder.fetchSource(includedFields, null);
        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        SearchHit[] searchHit = searchResponse.getHits().getHits();
        List<EventDetail> eventList = new ArrayList<>();
        for (SearchHit hit : searchHit) {
            eventList.add(objectMapper.convertValue(hit.getSourceAsMap(), EventDetail.class));
        }

        return eventList;
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
