package in.expensetrackerapp.document.controller;

import in.expensetrackerapp.auth.security.JwtAuthenticationEntryPoint;
import in.expensetrackerapp.auth.security.JwtAuthenticationFilter;
import in.expensetrackerapp.auth.security.JwtService;
import in.expensetrackerapp.auth.security.SecurityConfig;
import in.expensetrackerapp.common.exception.ConflictException;
import in.expensetrackerapp.common.exception.GlobalExceptionHandler;
import in.expensetrackerapp.common.exception.PayloadTooLargeException;
import in.expensetrackerapp.common.exception.ResourceNotFoundException;
import in.expensetrackerapp.common.exception.UnsupportedFileTypeException;
import in.expensetrackerapp.config.CorsConfig;
import in.expensetrackerapp.document.dto.DocumentResponse;
import in.expensetrackerapp.document.model.DocumentStatus;
import in.expensetrackerapp.document.model.UploadedDocument;
import in.expensetrackerapp.document.service.DocumentDownload;
import in.expensetrackerapp.document.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DocumentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        CorsConfig.class, GlobalExceptionHandler.class})
class DocumentControllerTest {

    private static final String TOKEN = "valid.jwt.token";
    private static final String USER_ID = "user-123";
    private static final String AUTH = "Bearer " + TOKEN;
    private static final byte[] PDF = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void authenticate() {
        when(jwtService.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(jwtService.isTokenValid(TOKEN, USER_ID)).thenReturn(true);
    }

    private static DocumentResponse response() {
        return new DocumentResponse("doc-1", "s.pdf", "application/pdf", PDF.length,
                DocumentStatus.UPLOADED, Instant.now(), Instant.now());
    }

    @Test
    void upload_returns201AndUsesPrincipalNotRequestSuppliedUserId() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "s.pdf", "application/pdf", PDF);
        when(documentService.upload(eq(USER_ID), any(MultipartFile.class))).thenReturn(response());

        mockMvc.perform(multipart("/api/documents").file(file)
                        .param("userId", "attacker-999")
                        .header("Authorization", AUTH))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/documents/doc-1"))
                .andExpect(jsonPath("$.id").value("doc-1"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.gridFsFileId").doesNotExist());

        verify(documentService).upload(eq(USER_ID), any(MultipartFile.class));
    }

    @Test
    void upload_withoutTokenIsUnauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "s.pdf", "application/pdf", PDF);

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upload_missingFilePartIsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/documents").header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_unsupportedTypeIs415() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.pdf", "application/pdf", "MZ".getBytes());
        when(documentService.upload(eq(USER_ID), any(MultipartFile.class))).thenThrow(new UnsupportedFileTypeException("Unsupported file type"));

        mockMvc.perform(multipart("/api/documents").file(file).header("Authorization", AUTH))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Unsupported file type"));
    }

    @Test
    void upload_oversizedFileIs413() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", PDF);
        when(documentService.upload(eq(USER_ID), any(MultipartFile.class))).thenThrow(new PayloadTooLargeException("too big"));

        mockMvc.perform(multipart("/api/documents").file(file).header("Authorization", AUTH))
                .andExpect(status().is(413));
    }

    @Test
    void upload_containerLevelSizeLimitIs413() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", PDF);
        when(documentService.upload(eq(USER_ID), any(MultipartFile.class))).thenThrow(new MaxUploadSizeExceededException(1024));

        mockMvc.perform(multipart("/api/documents").file(file).header("Authorization", AUTH))
                .andExpect(status().is(413));
    }

    @Test
    void list_returnsOwnDocuments() throws Exception {
        when(documentService.list(USER_ID, 0, 20)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/documents").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("doc-1"));
    }

    @Test
    void get_otherUsersDocumentIs404() throws Exception {
        when(documentService.get(eq(USER_ID), eq("doc-9"))).thenThrow(new ResourceNotFoundException("Document not found"));

        mockMvc.perform(get("/api/documents/doc-9").header("Authorization", AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Document not found"));
    }

    @Test
    void download_streamsFileWithSafeHeaders() throws Exception {
        UploadedDocument doc = new UploadedDocument();
        doc.setId("doc-1");
        doc.setOriginalFilename("statement.pdf");
        doc.setContentType("application/pdf");
        doc.setSizeBytes(PDF.length);
        when(documentService.download(USER_ID, "doc-1")).thenReturn(new DocumentDownload(doc, new ByteArrayResource(PDF)));

        mockMvc.perform(get("/api/documents/doc-1/download").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/documents/doc-1").header("Authorization", AUTH))
                .andExpect(status().isNoContent());

        verify(documentService).delete(USER_ID, "doc-1");
    }

    @Test
    void delete_whileProcessingIs409() throws Exception {
        org.mockito.Mockito.doThrow(new ConflictException("being processed"))
                .when(documentService).delete(USER_ID, "doc-1");

        mockMvc.perform(delete("/api/documents/doc-1").header("Authorization", AUTH))
                .andExpect(status().isConflict());
    }

    // ---- Regression tests for the GlobalExceptionHandler catch-all (previously turned these into 500) ----

    @Test
    void unsupportedHttpMethodIs405NotInternalServerError() throws Exception {
        mockMvc.perform(delete("/api/documents").header("Authorization", AUTH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void unknownPathIs404NotInternalServerError() throws Exception {
        mockMvc.perform(get("/api/does-not-exist").header("Authorization", AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unexpectedServiceFailureIs500WithGenericMessage() throws Exception {
        when(documentService.get(any(), any())).thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(get("/api/documents/doc-1").header("Authorization", AUTH))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected internal error occurred. Please try again later."));
    }
}
