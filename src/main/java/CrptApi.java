import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Класс для взаимодействия с API Честного знака.
 */
public class CrptApi {
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Semaphore rateLimiter;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * Конструктор класса CrptApi.
     *
     * @param timeUnit     Единица времени для ограничения количества запросов.
     * @param requestLimit Максимальное количество запросов в указанный промежуток времени.
     */
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        rateLimiter = new Semaphore(requestLimit);
        scheduler.scheduleAtFixedRate(() -> rateLimiter.release(requestLimit), 0, 1, timeUnit);
    }

    /**
     * Метод для загрузки и десериализации объекта Document из JSON файла.
     *
     * @return Объект Document, заполненный данными из файла.
     * @throws IOException Если файл не найден или произошла ошибка при чтении.
     */
    public Document loadDocumentFromJson() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream("/document.json")) {
            return objectMapper.readValue(inputStream, Document.class);
        }
    }

    /**
     * Метод для отправки документа на API Честного знака.
     *
     * @param document  Объект документа для отправки.
     * @param signature Подпись документа.
     * @return Ответ от сервера в виде строки.
     * @throws InterruptedException Если выполнение потока было прервано.
     * @throws IOException          Если произошла ошибка при отправке запроса.
     */
    public String createDocument(Document document, String signature) throws InterruptedException, IOException {
        rateLimiter.acquire();

        String requestBody = objectMapper.writeValueAsString(document);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .header("Content-Type", "application/json")
                .header("Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    /**
     * Завершает работу планировщика и освобождает ресурсы.
     */
    public void shutdown() {
        scheduler.shutdown();
    }

    /**
     * Внутренний класс для представления структуры документа.
     */
    public static class Document {
        public static class Description {
            @JsonProperty("participantInn")
            public String participantInn;
        }

        public static class Product {
            @JsonProperty("certificate_document")
            public String certificateDocument;

            @JsonProperty("certificate_document_date")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            public Date certificateDocumentDate;

            @JsonProperty("certificate_document_number")
            public String certificateDocumentNumber;

            @JsonProperty("owner_inn")
            public String ownerInn;

            @JsonProperty("producer_inn")
            public String producerInn;

            @JsonProperty("production_date")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            public Date productionDate;

            @JsonProperty("tnved_code")
            public String tnvedCode;

            @JsonProperty("uit_code")
            public String uitCode;

            @JsonProperty("uitu_code")
            public String uituCode;
        }

        @JsonProperty("description")
        public Description description;

        @JsonProperty("doc_id")
        public String docId;

        @JsonProperty("doc_status")
        public String docStatus;

        @JsonProperty("doc_type")
        public String docType;

        @JsonProperty("importRequest")
        public boolean importRequest;

        @JsonProperty("owner_inn")
        public String ownerInn;

        @JsonProperty("participant_inn")
        public String participantInn;

        @JsonProperty("producer_inn")
        public String producerInn;

        @JsonProperty("production_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        public Date productionDate;

        @JsonProperty("production_type")
        public String productionType;

        @JsonProperty("products")
        public List<Product> products;

        @JsonProperty("reg_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        public Date regDate;

        @JsonProperty("reg_number")
        public String regNumber;
    }

    // Пример использования класса CrptApi
    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 10); // Например, ограничение в 10 запросов в секунду
        try {
            Document document = api.loadDocumentFromJson(); // Загрузка документа из JSON файла
            String response = api.createDocument(document, "your-signature-here");
            System.out.println("Response: " + response);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            api.shutdown();
        }
    }
}
