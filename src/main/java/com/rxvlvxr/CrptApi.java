package com.rxvlvxr;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
public class CrptApi {
    // нужен для синхронизации
    private static final Object MONITOR = new Object();
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private static Bucket bucket;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;

        bucket = createBucket(timeUnit, requestLimit);
    }


    // метод для создания корзины токенов
    private static Bucket createBucket(TimeUnit timeUnit, int requestLimit) {
        return Bucket.builder()
                // создаем корзину
                // timeUnit - промежуток времени (секунда, минута, час и т.д.)
                // requestLimit - количество запросов
                // т.е. корзина будет ограничена requestLimit количеством запросов в timeUnit промежутке
                // через каждый timeUnit промежуток корзина будет пополняться
                .addLimit(Bandwidth.classic(requestLimit, Refill.intervally(requestLimit, Duration.of(1, timeUnit.toChronoUnit()))))
                .build();
    }

    private static void request(DocumentDTO doc, String sign) {
        // делаем поток безопасным (синхронизированным), используя монитор
        synchronized (MONITOR) {
            // проверяет есть ли доступные токены для запроса
            // если есть, то делаем запрос
            if (bucket.tryConsume(1)) {
                // импортировано из Spring Web для работы с клиентом
                final RestTemplate template = new RestTemplate();
                final HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                // добавляем подпись
                headers.add("Signature", sign);
                // создаем запрос с header
                final HttpEntity<DocumentDTO> request = new HttpEntity<>(doc, headers);
                try {
                    // POST запрос
                    template.postForObject("https://ismp.crpt.ru/api/v3/lk/documents/create", request, String.class);

                    // ловим исключение, т.к. ответ с сервера 401 Unauthorized
                } catch (HttpClientErrorException e) {
                    // допустим, что запрос прошел
                    System.out.println("OK");
                }
            } else {
                // если токенов нет, то блокируем запрос
                System.out.println("TOO_MANY_REQUESTS");
            }
        }
    }

    public static void main(String[] args) {
        // при создании объекта задаем единицу времени и максимально допустимое число запросов на API
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 5);
        // нужен для десериализации
        ObjectMapper mapper = new ObjectMapper();
        // нужен для десериализации LocalDate
        mapper.registerModule(new JavaTimeModule());
        DocumentDTO document;

        try {
            // десериализуем из JSON в объект типа DocumentDTO
            document = mapper.readValue
                    (
                            "{\"description\": " +
                                    "{ \"participantInn\": \"string\" }, " +
                                    "\"doc_id\": \"string\", " +
                                    "\"doc_status\": \"string\", " +
                                    "\"doc_type\": \"LP_INTRODUCE_GOODS\", " +
                                    "\"importRequest\": true, " +
                                    "\"owner_inn\": \"string\", " +
                                    "\"participant_inn\": \"string\", " +
                                    "\"producer_inn\":\"string\", " +
                                    "\"production_date\": \"2020-01-23\", " +
                                    "\"production_type\": \"string\"," +
                                    "\"products\": " +
                                    "[ " +
                                    "{ \"certificate_document\": \"string\"," +
                                    "\"certificate_document_date\": \"2020-01-23\"," +
                                    "\"certificate_document_number\": \"string\", " +
                                    "\"owner_inn\": \"string\"," +
                                    "\"producer_inn\": \"string\", " +
                                    "\"production_date\": \"2020-01-23\"," +
                                    "\"tnved_code\": \"string\", " +
                                    "\"uit_code\": \"string\", " +
                                    "\"uitu_code\": \"string\" } " +
                                    "]," +
                                    "\"reg_date\": \"2020-01-23\", " +
                                    "\"reg_number\": \"string\"}", DocumentDTO.class
                    );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        // иммитируем запросы из множества потоков
        for (int i = 0; i < 100; i++) {
            new Thread(() -> request(document, "SOME_SIGN")).start();
        }
    }


    // классы DTO нужны для десериализации
    @NoArgsConstructor
    @Getter
    @Setter
    private static class DocumentDTO {
        private DescriptionDTO description;
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
        private List<ProductDTO> products;
        @JsonProperty(value = "reg_date")
        private LocalDate regDate;
        @JsonProperty(value = "reg_number")
        private String regNumber;
    }

    // классы DTO нужны для десериализации
    @NoArgsConstructor
    @Getter
    @Setter
    private static class DescriptionDTO {
        @JsonProperty(value = "participantInn")
        private String participantInn;
    }

    // классы DTO нужны для десериализации
    @NoArgsConstructor
    @Setter
    @Getter
    private static class ProductDTO {
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
