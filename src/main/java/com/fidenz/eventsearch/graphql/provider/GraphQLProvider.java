package com.fidenz.eventsearch.graphql.provider;

import com.fidenz.eventsearch.graphql.fetcher.SearchDataFetchers;
import com.fidenz.eventsearch.graphql.fetcher.StatDataFetchers;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

@Component
public class GraphQLProvider {

    @Autowired
    private SearchDataFetchers searchDataFetchers;

    @Autowired
    private StatDataFetchers statDataFetchers;

    private GraphQL graphQL;

    @Bean
    public GraphQL graphQL() {
        return graphQL;
    }

    @PostConstruct
    public void init() throws IOException {
        URL url = Resources.getResource("schema.graphqls");
        String sdl = Resources.toString(url, Charsets.UTF_8);
        GraphQLSchema graphQLSchema = buildSchema(sdl);
        this.graphQL = GraphQL.newGraphQL(graphQLSchema).build();
    }

    private GraphQLSchema buildSchema(String sdl) {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring runtimeWiring = buildWiring();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private RuntimeWiring buildWiring() {
        return RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("eventDetailById", searchDataFetchers.getEventDetailByIdDataFetcher()))
                .type(newTypeWiring("Query")
                        .dataFetcher("search", searchDataFetchers.searchFetcher()))
                .type(newTypeWiring("Query")
                        .dataFetcher("findAll", searchDataFetchers.findAllFetcher()))
                .type(newTypeWiring("Query")
                        .dataFetcher("findAverages", statDataFetchers.getAverages()))
                .type(newTypeWiring("Query")
                        .dataFetcher("findCounter", statDataFetchers.getCounter()))
                .type(newTypeWiring("Query")
                        .dataFetcher("findCameras", statDataFetchers.getCameras()))
                .type(newTypeWiring("Query")
                        .dataFetcher("findEventTimeRange", statDataFetchers.getEventTimeRange()))
                .type(newTypeWiring("Query")
                        .dataFetcher("findCountByCategory", statDataFetchers.getEventByCategory()))
                .build();
    }

}