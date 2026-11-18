package com.swen3.dms.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swen3.dms.document.DocumentRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentApiIntegrationTest {

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
    DocumentRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void uploadedDocumentCanBeFetchedAndDownloaded() throws Exception {
        String id = upload("invoice.pdf", "application/pdf", "PDF content", "March invoice");

        mvc.perform(get("/api/documents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("March invoice"))
                .andExpect(jsonPath("$.filename").value("invoice.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(11));

        mvc.perform(get("/api/documents/{id}/content", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"invoice.pdf\""))
                .andExpect(content().string("PDF content"));
    }

    @Test
    void nonAsciiFileNamesAreEncodedForDownload() throws Exception {
        String id = upload("Rechnung März.pdf", "application/pdf", "x", null);

        mvc.perform(get("/api/documents/{id}/content", id))
                .andExpect(header().string("Content-Disposition",
                        containsString("filename*=UTF-8''Rechnung%20M%C3%A4rz.pdf")));
    }

    @Test
    void uploadReturnsLocationOfTheNewDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());

        mvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/documents/")));
    }

    @Test
    void titleDefaultsToFileNameWithoutClientPath() throws Exception {
        String id = upload("C:\\Users\\me\\notes.txt", "text/plain", "hello", null);

        mvc.perform(get("/api/documents/{id}", id))
                .andExpect(jsonPath("$.title").value("notes.txt"))
                .andExpect(jsonPath("$.filename").value("notes.txt"));
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        mvc.perform(multipart("/api/documents").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The uploaded file is empty."));
    }

    @Test
    void tooLongTitleIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());

        mvc.perform(multipart("/api/documents").file(file).param("title", "t".repeat(256)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsNewestFirstAndSearchesTitleAndFileNameIgnoringCase() throws Exception {
        upload("contract.pdf", "application/pdf", "1", "Rental Agreement");
        upload("holiday.jpg", "image/jpeg", "2", "Beach photo");
        upload("tax-2024.pdf", "application/pdf", "3", "Tax return");

        mvc.perform(get("/api/documents"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].title").value("Tax return"));

        mvc.perform(get("/api/documents").param("q", "AGREEMENT"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].filename").value("contract.pdf"));

        mvc.perform(get("/api/documents").param("q", ".pdf"))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void searchTreatsWildcardCharactersLiterally() throws Exception {
        upload("report.txt", "text/plain", "1", "Report");
        upload("growth.txt", "text/plain", "2", "Growth 100%");

        mvc.perform(get("/api/documents").param("q", "%"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Growth 100%"));

        mvc.perform(get("/api/documents").param("q", "_"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void documentCanBeRenamed() throws Exception {
        String id = upload("a.txt", "text/plain", "x", "Old title");

        mvc.perform(patch("/api/documents/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"  New title  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"));

        mvc.perform(get("/api/documents/{id}", id)).andExpect(jsonPath("$.title").value("New title"));
    }

    @Test
    void blankTitleIsRejectedOnRename() throws Exception {
        String id = upload("a.txt", "text/plain", "x", "Title");

        mvc.perform(patch("/api/documents/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteRemovesMetadataAndStoredFile() throws Exception {
        String id = upload("a.txt", "text/plain", "x", "Title");
        String storageKey = repository.findById(UUID.fromString(id)).orElseThrow().getStorageKey();
        assertThat(storageDir.resolve(storageKey)).exists();

        mvc.perform(delete("/api/documents/{id}", id)).andExpect(status().isNoContent());

        mvc.perform(get("/api/documents/{id}", id)).andExpect(status().isNotFound());
        assertThat(storageDir.resolve(storageKey)).doesNotExist();
    }

    @Test
    void unknownDocumentIsNotFound() throws Exception {
        mvc.perform(get("/api/documents/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", endsWith("does not exist.")));
        mvc.perform(delete("/api/documents/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void malformedIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/documents/{id}", "not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private String upload(String filename, String contentType, String content, String title) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType,
                content.getBytes(StandardCharsets.UTF_8));
        var request = multipart("/api/documents").file(file);
        if (title != null) {
            request.param("title", title);
        }
        String body = mvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        Thread.sleep(5); // distinct uploadedAt values keep the newest-first ordering deterministic
        return json.get("id").asText();
    }
}
