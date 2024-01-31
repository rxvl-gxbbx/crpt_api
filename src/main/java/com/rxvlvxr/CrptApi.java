package com.rxvlvxr;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private static final Object MONITOR = new Object();
    private static AtomicInteger count;
    private final TimeUnit timeUnit;
    private final int requestLimit;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        count = new AtomicInteger(requestLimit);
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
    }

    private static void create(DocumentDTO doc, String sign) throws InterruptedException {
        // делаем поток безопасным (синхронизированным), используя монитор
        synchronized (MONITOR) {
            // если счетчик равен 0, значит количество допустимых запросов превышено, поэтому выходим из метода
            if (count.get() == 0) {
                return;
            }
            // импортировано из Spring Web для работы с клиентом
            final RestTemplate template = new RestTemplate();
            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // добавляем подпись
            headers.add("Signature", sign);
            // создаем запрос
            final HttpEntity<DocumentDTO> request = new HttpEntity<>(doc, headers);
            // POST запрос
            template.postForObject("https://ismp.crpt.ru/api/v3/lk/documents/create", request, String.class);
        }
    }

    public static void main(String[] args) {
        // при создании объекта задаем в какой единице измерения будет назначен таймер
        // а также requestLimit - максимально допустимое число запросов на API
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5);

        ObjectMapper mapper = new ObjectMapper();
        // создаем ScheduledExecutorService чтобы можно было запускать поток по таймеру
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        // нужен для десериализации LocalDate
        mapper.registerModule(new JavaTimeModule());

        // объявляем переменную
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

        executor.scheduleAtFixedRate(() -> new Thread(() -> {
            // устанавливаем максимально допустимое значение потоков
            count.set(api.requestLimit);

            // иммитируем запросы от множества потоков
            for (int i = 0; i < 100; i++) {
                new Thread(() -> {
                    try {
                        create(document, "SOME_SIGN");
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } catch (HttpClientErrorException e) { // ловим ошибку 401, т.к. API ее выдает
                        count.getAndDecrement();
                        // логи
                        System.out.println("\"successful\" POST request = " + Thread.currentThread().getName());
                    }

                }).start();
            }
            // указываем единицу времени (в секундах) и период через который будет повторяться данный поток
        }).start(), 0, 5, api.timeUnit);
    }

    // классы DTO нужны для десериализации
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
        @JsonFormat(pattern = "yyyy-MM-dd")
        @JsonProperty(value = "production_date")
        private LocalDate productionDate;
        @JsonProperty("production_type")
        private String productionType;
        private List<ProductDTO> products;
        @JsonFormat(pattern = "yyyy-MM-dd")
        @JsonProperty(value = "reg_date")

        private LocalDate regDate;
        @JsonProperty(value = "reg_number")

        private String regNumber;

        public DocumentDTO() {
        }

        public DescriptionDTO getDescription() {
            return description;
        }

        public void setDescription(DescriptionDTO description) {
            this.description = description;
        }

        public String getDocId() {
            return docId;
        }

        public void setDocId(String docId) {
            this.docId = docId;
        }

        public String getDocStatus() {
            return docStatus;
        }

        public void setDocStatus(String docStatus) {
            this.docStatus = docStatus;
        }

        public String getDocType() {
            return docType;
        }

        public void setDocType(String docType) {
            this.docType = docType;
        }

        public boolean isImportRequest() {
            return importRequest;
        }

        public void setImportRequest(boolean importRequest) {
            this.importRequest = importRequest;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        public LocalDate getProductionDate() {
            return productionDate;
        }

        public void setProductionDate(LocalDate productionDate) {
            this.productionDate = productionDate;
        }

        public String getProductionType() {
            return productionType;
        }

        public void setProductionType(String productionType) {
            this.productionType = productionType;
        }

        public List<ProductDTO> getProducts() {
            return products;
        }

        public void setProducts(List<ProductDTO> products) {
            this.products = products;
        }

        public LocalDate getRegDate() {
            return regDate;
        }

        public void setRegDate(LocalDate regDate) {
            this.regDate = regDate;
        }

        public String getRegNumber() {
            return regNumber;
        }

        public void setRegNumber(String regNumber) {
            this.regNumber = regNumber;
        }
    }

    // классы DTO нужны для десериализации
    private static class DescriptionDTO {
        @JsonProperty(value = "participantInn")
        private String participantInn;

        public DescriptionDTO() {
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }
    }

    // классы DTO нужны для десериализации
    private static class ProductDTO {
        @JsonProperty(value = "certificate_document")
        private String certificateDocument;
        @JsonFormat(pattern = "yyyy-MM-dd")
        @JsonProperty(value = "certificate_document_date")
        private LocalDate certificateDocumentDate;
        @JsonProperty(value = "certificate_document_number")
        private String certificateDocumentNumber;
        @JsonProperty(value = "owner_inn")
        private String ownerInn;
        @JsonProperty(value = "producer_inn")
        private String producerInn;
        @JsonFormat(pattern = "yyyy-MM-dd")
        @JsonProperty(value = "production_date")
        private LocalDate productionDate;
        @JsonProperty(value = "tnved_code")
        private String tnvedCode;
        @JsonProperty(value = "uit_code")
        private String uitCode;
        @JsonProperty(value = "uitu_code")
        private String uituCode;

        public ProductDTO() {
        }

        public String getCertificateDocument() {
            return certificateDocument;
        }

        public void setCertificateDocument(String certificateDocument) {
            this.certificateDocument = certificateDocument;
        }

        public LocalDate getCertificateDocumentDate() {
            return certificateDocumentDate;
        }

        public void setCertificateDocumentDate(LocalDate certificateDocumentDate) {
            this.certificateDocumentDate = certificateDocumentDate;
        }

        public String getCertificateDocumentNumber() {
            return certificateDocumentNumber;
        }

        public void setCertificateDocumentNumber(String certificateDocumentNumber) {
            this.certificateDocumentNumber = certificateDocumentNumber;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        public LocalDate getProductionDate() {
            return productionDate;
        }

        public void setProductionDate(LocalDate productionDate) {
            this.productionDate = productionDate;
        }

        public String getTnvedCode() {
            return tnvedCode;
        }

        public void setTnvedCode(String tnvedCode) {
            this.tnvedCode = tnvedCode;
        }

        public String getUitCode() {
            return uitCode;
        }

        public void setUitCode(String uitCode) {
            this.uitCode = uitCode;
        }

        public String getUituCode() {
            return uituCode;
        }

        public void setUituCode(String uituCode) {
            this.uituCode = uituCode;
        }
    }
}
