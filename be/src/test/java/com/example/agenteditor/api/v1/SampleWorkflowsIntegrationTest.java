package com.example.agenteditor.api.v1;

import com.example.agenteditor.llm.OpenRouterChatModelFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(SampleWorkflowsIntegrationTest.SampleWorkflowTestConfig.class)
@DisplayName("Sample workflows integration")
class SampleWorkflowsIntegrationTest {

    @TestConfiguration
    static class SampleWorkflowTestConfig {
        @Bean
        @Primary
        OpenRouterChatModelFactory openRouterChatModelFactory() {
            return new OpenRouterChatModelFactory("test-key", "https://test", "test-model") {
                @Override
                public ChatModel build(String baseUrl, String modelName) {
                    return new SampleWorkflowSmartStubModel();
                }
            };
        }

        @Bean
        RestTemplate restTemplate() {
            RestTemplate rest = new RestTemplate();
            rest.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
                @Override
                public boolean hasError(ClientHttpResponse response) {
                    return false;
                }

                @Override
                public void handleError(java.net.URI url, HttpMethod method, ClientHttpResponse response) {
                }
            });
            return rest;
        }
    }

    static class SampleWorkflowSmartStubModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            String joined = "";
            if (chatRequest != null && chatRequest.messages() != null) {
                joined = chatRequest.messages().stream()
                        .map(ChatMessage::toString)
                        .collect(Collectors.joining("\n"))
                        .toLowerCase();
            }
            String reply;
            if (joined.contains("planner expert that is provided with a set of agents")) {
                if (joined.contains("last received response is: ''")) {
                    reply = "{\"agentName\":\"Assistant\",\"arguments\":{}}";
                } else {
                    reply = "{\"agentName\":\"done\",\"arguments\":{\"response\":\"SUPERVISOR_DONE\"}}";
                }
            } else if (joined.contains("classify this request")) {
                reply = "GENERAL";
            } else if (joined.contains("movie option:")) {
                reply = "FINAL_EVENING_PLAN";
            } else if (joined.contains("suggest one movie")) {
                reply = "MOVIE_OPTION";
            } else if (joined.contains("suggest one dinner")) {
                reply = "DINNER_OPTION";
            } else if (joined.contains("write a short story")) {
                reply = "STORY_DRAFT";
            } else if (joined.contains("refine the draft") || joined.contains("edit for style and clarity")) {
                reply = "STORY_EDITED";
            } else if (joined.contains("answer clearly")) {
                reply = "GENERAL_RESPONSE";
            } else if (joined.contains("respond creatively")) {
                reply = "CREATIVE_RESPONSE";
            } else if (joined.contains("create a practical plan")) {
                reply = "PLANNING_RESPONSE";
            } else if (joined.contains("help with")) {
                reply = "ASSISTANT_HELP";
            } else {
                reply = "STUB_OK";
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(reply))
                    .finishReason(FinishReason.STOP)
                    .build();
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private RestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/workflows";
    }

    @Test
    @DisplayName("all sample workflows run successfully with deterministic outputs")
    void allSampleWorkflowsRunAsExpected() {
        ResponseEntity<Map<String, Object>> samplesResp = restTemplate.exchange(
                baseUrl() + "/samples",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(samplesResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(samplesResp.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sampleItems = (List<Map<String, Object>>) samplesResp.getBody().get("workflows");
        assertThat(sampleItems).isNotNull();

        Map<String, String> expectedByName = Map.of(
                "Story workflow", "STORY_EDITED",
                "Evening plan workflow", "FINAL_EVENING_PLAN",
                "Expert router workflow", "GENERAL_RESPONSE",
                "Supervisor workflow", "ASSISTANT_HELP"
        );

        Map<String, String> sampleIdByName = new LinkedHashMap<>();
        for (Map<String, Object> item : sampleItems) {
            sampleIdByName.put(String.valueOf(item.get("name")), String.valueOf(item.get("id")));
        }

        assertThat(sampleIdByName.keySet()).containsAll(expectedByName.keySet());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        for (Map.Entry<String, String> entry : expectedByName.entrySet()) {
            String sampleName = entry.getKey();
            String expected = entry.getValue();
            String workflowId = sampleIdByName.get(sampleName);

            ResponseEntity<Map<String, Object>> runResp = restTemplate.exchange(
                    baseUrl() + "/" + workflowId + "/run",
                    HttpMethod.POST,
                    new HttpEntity<>(sampleInput(sampleName), headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(runResp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(runResp.getBody()).isNotNull();
            assertThat(runResp.getBody()).containsKey("result");
            assertThat(runResp.getBody()).containsKey("executedNodeIds");
            String result = String.valueOf(runResp.getBody().get("result"));
            assertThat(result).isNotBlank();
            if ("Supervisor workflow".equals(sampleName)) {
                assertThat(result).containsAnyOf("ASSISTANT_HELP", "SUPERVISOR_DONE");
            } else {
                assertThat(result).contains(expected);
            }
        }
    }

    private static Map<String, Object> sampleInput(String sampleName) {
        return switch (sampleName) {
            case "Story workflow" -> Map.of(
                    "metadata", Map.of(
                            "prompt", "Write a short noir story.",
                            "topic", "a robot in Paris",
                            "style", "noir"
                    )
            );
            case "Evening plan workflow" -> Map.of(
                    "metadata", Map.of(
                            "prompt", "Suggest an evening plan.",
                            "mood", "cozy"
                    )
            );
            case "Expert router workflow" -> Map.of(
                    "metadata", Map.of(
                            "prompt", "What time is it right now?"
                    )
            );
            case "Supervisor workflow" -> Map.of(
                    "metadata", Map.of(
                            "prompt", "Suggest one cozy movie and one dinner idea.",
                            "topic", "a robot in Paris",
                            "style", "noir",
                            "mood", "cozy"
                    )
            );
            default -> Map.of("metadata", Map.of("prompt", "test"));
        };
    }
}
