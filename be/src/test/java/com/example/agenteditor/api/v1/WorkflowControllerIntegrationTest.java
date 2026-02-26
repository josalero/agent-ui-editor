package com.example.agenteditor.api.v1;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(WorkflowControllerIntegrationTest.JsonMapperTestConfig.class)
@DisplayName("Workflow CRUD API")
class WorkflowControllerIntegrationTest {

    @TestConfiguration
    static class JsonMapperTestConfig {
        @Bean
        public tools.jackson.databind.json.JsonMapper jsonMapper() {
            return tools.jackson.databind.json.JsonMapper.builder().build();
        }

        @Bean
        public RestTemplate restTemplate() {
            RestTemplate rest = new RestTemplate();
            rest.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
                @Override
                public boolean hasError(ClientHttpResponse response) {
                    return false;
                }

                @Override
                public void handleError(java.net.URI url, HttpMethod method, ClientHttpResponse response) throws java.io.IOException {
                }
            });
            return rest;
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private RestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/workflows";
    }

    private static final String STORY_JSON = """
            {
              "name": "Story workflow",
              "entryNodeId": "seq-story",
              "nodes": [
                { "id": "llm-1", "type": "llm", "baseUrl": "https://openrouter.ai/api/v1", "modelName": "openai/gpt-4o-mini" },
                { "id": "writer", "type": "agent", "llmId": "llm-1", "name": "CreativeWriter", "outputKey": "story" },
                { "id": "editor", "type": "agent", "llmId": "llm-1", "name": "StyleEditor", "outputKey": "story" },
                { "id": "seq-story", "type": "sequence", "subAgentIds": ["writer", "editor"], "outputKey": "story" }
              ]
            }
            """;

    @Nested
    @DisplayName("full CRUD with story workflow")
    class FullCrud {

        @Test
        @DisplayName("POST create returns 201 and id; GET by id returns full graph; list contains it; PUT updates; DELETE returns 204; GET after delete returns 404")
        void createGetListUpdateDelete() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map<String, Object>> createResp = restTemplate.exchange(
                    baseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(STORY_JSON, headers),
                    new ParameterizedTypeReference<>() {}
            );
            assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(createResp.getBody()).isNotNull();
            String id = (String) createResp.getBody().get("id");
            assertThat(id).isNotBlank();

            ResponseEntity<Map> getResp = restTemplate.getForEntity(baseUrl() + "/" + id, Map.class);
            assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> getBody = getResp.getBody();
            assertThat(getBody).isNotNull();
            assertThat(getBody.get("id")).isEqualTo(id);
            assertThat(getBody.get("name")).isEqualTo("Story workflow");
            assertThat(getBody.get("entryNodeId")).isEqualTo("seq-story");
            assertThat(getBody.get("nodes")).asList().hasSize(4);
            assertThat(getBody.get("createdAt")).isNotNull();
            assertThat(getBody.get("updatedAt")).isNotNull();

            ResponseEntity<Map<String, Object>> listResp = restTemplate.exchange(baseUrl(), HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {});
            assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> listBody = listResp.getBody();
            assertThat(listBody).isNotNull();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> workflows = (List<Map<String, Object>>) listBody.get("workflows");
            assertThat(workflows).anyMatch(w -> id.equals(String.valueOf(w.get("id"))));

            String updateJson = STORY_JSON.replace("\"name\": \"Story workflow\"", "\"name\": \"Story workflow updated\"");
            ResponseEntity<Map> updateResp = restTemplate.exchange(
                    baseUrl() + "/" + id,
                    HttpMethod.PUT,
                    new HttpEntity<>(updateJson, headers),
                    new ParameterizedTypeReference<>() {}
            );
            assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updateResp.getBody()).isNotNull();
            assertThat(updateResp.getBody().get("name")).isEqualTo("Story workflow updated");

            ResponseEntity<Void> deleteResp = restTemplate.exchange(baseUrl() + "/" + id, HttpMethod.DELETE, null, Void.class);
            assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            ResponseEntity<Map> getAfterDelete = restTemplate.getForEntity(baseUrl() + "/" + id, Map.class);
            assertThat(getAfterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        @DisplayName("GET non-existent id returns 404")
        void getNotFoundReturns404() {
            String randomId = UUID.randomUUID().toString();
            ResponseEntity<Map> resp = restTemplate.getForEntity(baseUrl() + "/" + randomId, Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().get("message")).isNotNull();
        }

        @Test
        @DisplayName("DELETE non-existent id returns 404")
        void deleteNotFoundReturns404() {
            String randomId = UUID.randomUUID().toString();
            ResponseEntity<Void> resp = restTemplate.exchange(baseUrl() + "/" + randomId, HttpMethod.DELETE, null, Void.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("POST with invalid graph (entryNodeId not in nodes) returns 400 with errors")
        void createInvalidGraphReturns400() {
            String invalidJson = """
                    { "name": "Bad", "entryNodeId": "missing", "nodes": [
                      { "id": "n1", "type": "llm" }
                    ]}
                    """;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    baseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(invalidJson, headers),
                    new ParameterizedTypeReference<>() {}
            );
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().get("message")).isNotNull();
            assertThat(resp.getBody().get("errors")).asList().isNotEmpty();
        }
    }

    @Nested
    @DisplayName("run API")
    class RunApi {

        @Test
        @DisplayName("POST run with non-existent workflow id returns 404")
        void runNotFoundReturns404() {
            String randomId = UUID.randomUUID().toString();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl() + "/" + randomId + "/run",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("topic", "test", "style", "noir"), headers),
                    new ParameterizedTypeReference<>() {}
            );
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().get("message")).isNotNull();
        }

        @Test
        @DisplayName("POST run with valid workflow returns 200 with result or 500 on execution failure")
        void runWithValidWorkflowReturns200Or500() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map<String, Object>> createResp = restTemplate.exchange(
                    baseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(STORY_JSON, headers),
                    new ParameterizedTypeReference<>() {}
            );
            assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String id = (String) createResp.getBody().get("id");

            ResponseEntity<Map> runResp = restTemplate.exchange(
                    baseUrl() + "/" + id + "/run",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("topic", "a robot", "style", "noir"), headers),
                    new ParameterizedTypeReference<>() {}
            );
            assertThat(runResp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(runResp.getBody()).isNotNull();
            if (runResp.getStatusCode() == HttpStatus.OK) {
                assertThat(runResp.getBody()).containsKey("result");
            }
        }
    }
}
