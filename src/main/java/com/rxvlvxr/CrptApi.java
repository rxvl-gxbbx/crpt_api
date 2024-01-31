package com.rxvlvxr;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private final Semaphore semaphore;

    private static final AtomicInteger DELAY = new AtomicInteger(1);

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.semaphore = new Semaphore(requestLimit);
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> this.semaphore.release(requestLimit), DELAY.get(), DELAY.get(), timeUnit);
    }

    public static void main(String[] args) {
        String json =
                """
                        {
                            "description": {
                                "participantInn": "string"
                            },
                            "doc_id": "string",
                            "doc_status": "string",
                            "doc_type": "LP_INTRODUCE_GOODS",
                            109 "importRequest": true,
                            "owner_inn": "string",
                            "participant_inn": "string",
                            "producer_inn": "string",
                            "production_date": "2020-01-23",
                            "production_type": "string",
                            "products": [
                                {
                                    "certificate_document": "string",
                                    "certificate_document_date": "2020-01-23",
                                    "certificate_document_number": "string",
                                    "owner_inn": "string",
                                    "producer_inn": "string",
                                    "production_date": "2020-01-23",
                                    "tnved_code": "string",
                                    "uit_code": "string",
                                    "uitu_code": "string"
                                }
                            ],
                            "reg_date": "2020-01-23",
                            "reg_number": "string"
                        }""";

        json = repairJson(json);
        DocumentDto dto = getDocumentDto(json);

        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5);

        api.createDocument(dto, "УКЭП в Base64");
    }

    // ===================================================================================================================
    // = Implementation
    // ===================================================================================================================

    private void createDocument(DocumentDto request, String signature) {
        try {
            semaphore.acquire();
            String response = postRequest(request);
        } catch (InterruptedException | HttpClientErrorException e) {
            System.out.println(e.getMessage());
        }
    }

    private String postRequest(DocumentDto dto) {
        HttpHeaders headers = new HttpHeaders();
        RestTemplate restTemplate = new RestTemplate();

        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("JWT");

        return restTemplate.postForObject("https://ismp.crpt.ru/api/v3/lk/documents/create", new HttpEntity<>(dto, headers), String.class);
    }

    private static DocumentDto getDocumentDto(String json) {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());

        DocumentDto dto;

        try {
            dto = om.readValue(json, DocumentDto.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return dto;
    }

    private static String repairJson(String json) {
        int firstIndex = json.indexOf("{");
        int lastIndex = json.lastIndexOf("}");

        String[] s = json.substring(firstIndex + 1, lastIndex).split(",");

        for (int i = 0; i < s.length; i++) {
            String line = s[i];

            if (!line.trim().startsWith("\""))
                s[i] = line.substring(line.indexOf("\""));
        }

        return "{" + String.join(",", s) + "}";
    }

    @Getter
    @Setter
    private static class DocumentDto {
        private DescriptionDto description;
        @JsonProperty(value = "doc_id")
        private String docId;
        @JsonProperty(value = "doc_status")
        private String docStatus;
        @JsonProperty(value = "doc_type")
        private String docType;
        private boolean importRequest;
        @JsonProperty("owner_inn")
        private String ownerInn;
        @JsonProperty("participant_inn")
        private String participantInn;
        @JsonProperty("producer_inn")
        private String producerInn;
        @JsonProperty(value = "production_date")
        private LocalDate productionDate;
        @JsonProperty("production_type")
        private String productionType;
        private List<ProductDto> products;
        @JsonProperty(value = "reg_date")
        private LocalDate regDate;
        @JsonProperty(value = "reg_number")
        private String regNumber;
    }

    @Getter
    @Setter
    private static class DescriptionDto {
        @JsonProperty(value = "participantInn")
        private String participantInn;
    }

    @Getter
    @Setter
    private static class ProductDto {
        @JsonProperty(value = "certificate_document")
        private String certificateDocument;
        @JsonProperty(value = "certificate_document_date")
        private LocalDate certificateDocumentDate;
        @JsonProperty(value = "certificate_document_number")
        private String certificateDocumentNumber;
        @JsonProperty(value = "owner_inn")
        private String ownerInn;
        @JsonProperty(value = "producer_inn")
        private String producerInn;
        @JsonProperty(value = "production_date")
        private LocalDate productionDate;
        @JsonProperty(value = "tnved_code")
        private String tnvedCode;
        @JsonProperty(value = "uit_code")
        private String uitCode;
        @JsonProperty(value = "uitu_code")
        private String uituCode;
    }
}