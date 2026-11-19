package com.swen3.dms.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swen3.dms.document.DocumentMatch;
import com.swen3.dms.document.DocumentRepository;
import com.swen3.dms.document.DocumentService;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "dms.search.elasticsearch.enabled=true")
@AutoConfigureMockMvc
@Testcontainers
class ElasticsearchSearchIntegrationTest {

    @Container
    @ServiceConnection
    static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.18.8")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storageLocation(DynamicPropertyRegistry registry) {
        registry.add("dms.storage.location", storageDir::toString);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DocumentService documentService;

    @Autowired
    DocumentRepository repository;

    @Autowired
    DocumentIndexer indexer;

    @Autowired
    ElasticsearchClient client;

    @Autowired
    ElasticsearchProperties properties;

    @BeforeEach
    void reset() throws Exception {
        repository.deleteAll();
        client.deleteByQuery(request -> request.index(properties.index()).query(q -> q.matchAll(all -> all))
                .refresh(true));
    }

    @Test
    void findsExtractedTextWithSnippet() throws Exception {
        UUID invoice = upload("scan-001.pdf", "Scan 1");
        upload("scan-002.pdf", "Scan 2");
        complete(invoice, "Rechnung Nr. 4711 vom 3. März. Gesamtbetrag 120,00 EUR");

        List<DocumentMatch> matches = documentService.list("Gesamtbetrag");

        assertThat(ids(matches)).containsExactly(invoice);
        assertThat(matches.get(0).snippet()).contains("Gesamtbetrag 120,00 EUR");
    }

    @Test
    void matchesPrefixesWhileTyping() throws Exception {
        UUID contract = upload("lease.pdf", "Mietvertrag Wohnung");

        assertThat(ids(documentService.list("Mietv"))).containsExactly(contract);
        assertThat(ids(documentService.list("mietvertrag wohn"))).containsExactly(contract);
    }

    @Test
    void toleratesTyposAndOcrErrors() throws Exception {
        UUID scan = upload("scan.png", "Scan");
        complete(scan, "Versicherungspolizze Haushalt");

        assertThat(ids(documentService.list("Versicherungspolize"))).containsExactly(scan);
    }

    @Test
    void ranksTitleMatchesFirst() throws Exception {
        UUID inContent = upload("a.pdf", "Letter");
        complete(inContent, "This letter mentions the budget once.");
        UUID inTitle = upload("b.pdf", "Budget 2026");

        assertThat(ids(documentService.list("budget"))).containsExactly(inTitle, inContent);
    }

    @Test
    void followsRenamesAndDeletes() throws Exception {
        UUID id = upload("notes.txt", "Old name");

        mvc.perform(patch("/api/documents/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Renamed\"}"));
        assertThat(ids(documentService.list("renamed"))).containsExactly(id);
        assertThat(documentService.list("old")).isEmpty();

        mvc.perform(delete("/api/documents/{id}", id));
        assertThat(documentService.list("renamed")).isEmpty();
    }

    @Test
    void rebuildsMissingIndexFromDatabase() throws Exception {
        UUID id = upload("report.pdf", "Quarterly report");
        client.indices().delete(request -> request.index(properties.index()));

        indexer.createIndexIfMissing();

        assertThat(ids(documentService.list("quarterly"))).containsExactly(id);
    }

    private UUID upload(String filename, String title) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/pdf", new byte[] {1});
        String body = mvc.perform(multipart("/api/documents").file(file).param("title", title))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private void complete(UUID id, String text) {
        documentService.completeTextExtraction(id, text);
    }

    private static List<UUID> ids(List<DocumentMatch> matches) {
        return matches.stream().map(match -> match.document().getId()).toList();
    }
}
