package in.expensetrackerapp.document.service;

import in.expensetrackerapp.common.exception.BadRequestException;
import in.expensetrackerapp.common.exception.ConflictException;
import in.expensetrackerapp.common.exception.PayloadTooLargeException;
import in.expensetrackerapp.common.exception.ResourceNotFoundException;
import in.expensetrackerapp.common.exception.UnsupportedFileTypeException;
import in.expensetrackerapp.document.dto.DocumentResponse;
import in.expensetrackerapp.document.model.DocumentStatus;
import in.expensetrackerapp.document.model.UploadedDocument;
import in.expensetrackerapp.document.repository.DocumentRepository;
import in.expensetrackerapp.job.model.ProcessingJob;
import in.expensetrackerapp.job.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final String GRIDFS_ID = "507f1f77bcf86cd799439011";

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj<<>>endobj\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0};

    @Mock
    private DocumentRepository repository;

    @Mock
    private GridFsStorageService storage;

    @Mock
    private JobService jobService;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(repository, storage, jobService, "1KB");
    }

    private void stubSuccessfulStorageAndInsert() {
        when(storage.store(any(InputStream.class), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    ((InputStream) inv.getArgument(0)).readAllBytes();   // consume so the SHA-256 is computed
                    return GRIDFS_ID;
                });
        when(repository.insert(any(UploadedDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        ProcessingJob job = new ProcessingJob();
        job.setId("job-1");
        when(jobService.createJob(anyString(), anyString(), anyString())).thenReturn(job);
    }

    @Test
    void uploadPdf_storesFileAndMetadataForAuthenticatedUser() throws Exception {
        stubSuccessfulStorageAndInsert();
        MockMultipartFile file = new MockMultipartFile("file", "../../evil statement.pdf", "application/pdf", PDF);

        DocumentResponse response = service.upload("user-1", file);

        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(response.status()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(response.originalFilename()).isEqualTo("evil statement.pdf");
        assertThat(response.sizeBytes()).isEqualTo(PDF.length);
        assertThat(response.id()).isNotBlank();
        assertThat(response.jobId()).isEqualTo("job-1");

        ArgumentCaptor<UploadedDocument> captor = ArgumentCaptor.forClass(UploadedDocument.class);
        verify(repository).insert(captor.capture());
        UploadedDocument saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getGridFsFileId()).isEqualTo(GRIDFS_ID);
        assertThat(saved.getStoredFilename()).matches("[0-9a-f-]{36}\\.pdf");
        assertThat(saved.getSha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PDF)));
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        verify(storage).store(any(InputStream.class), eq(saved.getStoredFilename()), eq("application/pdf"),
                eq("user-1"), eq(saved.getId()));
        verify(jobService).createJob("user-1", saved.getId(), GRIDFS_ID);
    }

    @Test
    void uploadJpeg_isAcceptedAndStoredWithJpgExtension() {
        stubSuccessfulStorageAndInsert();
        MockMultipartFile file = new MockMultipartFile("file", "bill.jpeg", "image/jpeg", JPEG);

        DocumentResponse response = service.upload("user-1", file);

        assertThat(response.contentType()).isEqualTo("image/jpeg");
        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        verify(storage).store(any(InputStream.class), name.capture(), eq("image/jpeg"), eq("user-1"), anyString());
        assertThat(name.getValue()).endsWith(".jpg");
    }

    @Test
    void uploadPng_isAccepted() {
        stubSuccessfulStorageAndInsert();
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", PNG);

        assertThat(service.upload("user-1", file).contentType()).isEqualTo("image/png");
    }

    @Test
    void upload_acceptsGenericDeclaredTypeWhenSignatureIsValid() {
        stubSuccessfulStorageAndInsert();
        MockMultipartFile file = new MockMultipartFile("file", "s.pdf", "application/octet-stream", PDF);

        assertThat(service.upload("user-1", file).contentType()).isEqualTo("application/pdf");
    }

    @Test
    void upload_rejectsSpoofedMimeType_executableDeclaredAsPdf() {
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", EXE);

        assertThatThrownBy(() -> service.upload("user-1", file)).isInstanceOf(UnsupportedFileTypeException.class);
        verifyNoInteractions(storage, repository);
    }

    @Test
    void upload_rejectsDeclaredTypeThatContradictsSignature() {
        MockMultipartFile file = new MockMultipartFile("file", "x.pdf", "application/pdf", PNG);

        assertThatThrownBy(() -> service.upload("user-1", file))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessageContaining("does not match");
        verifyNoInteractions(storage, repository);
    }

    @Test
    void upload_rejectsUnsupportedTextFile() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain",
                "just text here".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> service.upload("user-1", file)).isInstanceOf(UnsupportedFileTypeException.class);
        verifyNoInteractions(storage, repository);
    }

    @Test
    void upload_rejectsOversizedFile() {
        byte[] big = new byte[2048];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", big);

        assertThatThrownBy(() -> service.upload("user-1", file)).isInstanceOf(PayloadTooLargeException.class);
        verifyNoInteractions(storage, repository);
    }

    @Test
    void upload_rejectsEmptyAndMissingFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "e.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.upload("user-1", empty)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.upload("user-1", null)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(storage, repository);
    }

    @Test
    void upload_removesGridFsFileWhenMetadataInsertFails() {
        when(storage.store(any(InputStream.class), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    ((InputStream) inv.getArgument(0)).readAllBytes();
                    return GRIDFS_ID;
                });
        when(repository.insert(any(UploadedDocument.class))).thenThrow(new IllegalStateException("db down"));
        MockMultipartFile file = new MockMultipartFile("file", "s.pdf", "application/pdf", PDF);

        assertThatThrownBy(() -> service.upload("user-1", file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
        verify(storage).deleteQuietly(GRIDFS_ID);
    }

    @Test
    void upload_rollsBackMetadataAndGridFsFileWhenJobCreationFails() {
        when(storage.store(any(InputStream.class), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    ((InputStream) inv.getArgument(0)).readAllBytes();
                    return GRIDFS_ID;
                });
        when(repository.insert(any(UploadedDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jobService.createJob(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("job db down"));
        MockMultipartFile file = new MockMultipartFile("file", "s.pdf", "application/pdf", PDF);

        assertThatThrownBy(() -> service.upload("user-1", file)).isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<UploadedDocument> inserted = ArgumentCaptor.forClass(UploadedDocument.class);
        verify(repository).insert(inserted.capture());
        verify(repository).deleteById(inserted.getValue().getId());
        verify(storage).deleteQuietly(GRIDFS_ID);
    }

    @Test
    void upload_doesNotPersistMetadataWhenGridFsStoreFails() {
        when(storage.store(any(InputStream.class), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("gridfs down"));
        MockMultipartFile file = new MockMultipartFile("file", "s.pdf", "application/pdf", PDF);

        assertThatThrownBy(() -> service.upload("user-1", file)).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).insert(any(UploadedDocument.class));
        verifyNoInteractions(jobService);
    }

    @Test
    void get_returnsOnlyOwnedDocument() {
        UploadedDocument doc = document("doc-1", "user-1", DocumentStatus.UPLOADED);
        when(repository.findByIdAndUserId("doc-1", "user-1")).thenReturn(Optional.of(doc));

        assertThat(service.get("user-1", "doc-1").id()).isEqualTo("doc-1");
    }

    @Test
    void get_otherUsersDocumentIsReportedAsNotFound() {
        when(repository.findByIdAndUserId("doc-1", "user-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("user-2", "doc-1")).isInstanceOf(ResourceNotFoundException.class);
        verify(repository).findByIdAndUserId("doc-1", "user-2");
    }

    @Test
    void download_otherUsersDocumentIsNotFoundAndStorageIsNotTouched() {
        when(repository.findByIdAndUserId("doc-1", "user-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download("user-2", "doc-1")).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void download_returnsResourceForOwner() {
        UploadedDocument doc = document("doc-1", "user-1", DocumentStatus.UPLOADED);
        when(repository.findByIdAndUserId("doc-1", "user-1")).thenReturn(Optional.of(doc));
        ByteArrayResource resource = new ByteArrayResource(PDF);
        when(storage.open(GRIDFS_ID)).thenReturn(Optional.of(fakeGridFsResource(resource)));

        DocumentDownload download = service.download("user-1", "doc-1");

        assertThat(download.document().getId()).isEqualTo("doc-1");
        assertThat(download.resource()).isNotNull();
    }

    @Test
    void download_reportsNotFoundWhenGridFsFileIsMissing() {
        UploadedDocument doc = document("doc-1", "user-1", DocumentStatus.UPLOADED);
        when(repository.findByIdAndUserId("doc-1", "user-1")).thenReturn(Optional.of(doc));
        when(storage.open(GRIDFS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download("user-1", "doc-1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesMetadataThenGridFsFile() {
        UploadedDocument doc = document("doc-1", "user-1", DocumentStatus.PROCESSED);
        when(repository.findByIdAndUserId("doc-1", "user-1")).thenReturn(Optional.of(doc));

        service.delete("user-1", "doc-1");

        InOrder order = inOrder(repository, storage);
        order.verify(repository).deleteById("doc-1");
        order.verify(storage).deleteQuietly(GRIDFS_ID);
    }

    @Test
    void delete_isRefusedWhileDocumentIsBeingProcessed() {
        UploadedDocument doc = document("doc-1", "user-1", DocumentStatus.PROCESSING);
        when(repository.findByIdAndUserId("doc-1", "user-1")).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.delete("user-1", "doc-1")).isInstanceOf(ConflictException.class);
        verify(repository, never()).deleteById(anyString());
        verifyNoInteractions(storage);
    }

    @Test
    void delete_otherUsersDocumentDeletesNothing() {
        when(repository.findByIdAndUserId("doc-1", "user-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("user-2", "doc-1")).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).deleteById(anyString());
        verifyNoInteractions(storage);
    }

    @Test
    void list_isScopedToUserAndCapsPageSize() {
        UploadedDocument doc = document("doc-1", "user-1", DocumentStatus.UPLOADED);
        when(repository.findByUserId(eq("user-1"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(doc)));

        List<DocumentResponse> result = service.list("user-1", -5, 100000);

        assertThat(result).hasSize(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserId(eq("user-1"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(DocumentService.MAX_PAGE_SIZE);
    }

    private static UploadedDocument document(String id, String userId, DocumentStatus status) {
        UploadedDocument d = new UploadedDocument();
        d.setId(id);
        d.setUserId(userId);
        d.setOriginalFilename("s.pdf");
        d.setStoredFilename("x.pdf");
        d.setContentType("application/pdf");
        d.setSizeBytes(PDF.length);
        d.setGridFsFileId(GRIDFS_ID);
        d.setStatus(status);
        d.setCreatedAt(Instant.now());
        d.setUpdatedAt(Instant.now());
        return d;
    }

    /** GridFsResource needs a GridFSFile; Mockito mock keeps this a pure unit test. */
    private static org.springframework.data.mongodb.gridfs.GridFsResource fakeGridFsResource(ByteArrayResource ignored) {
        return org.mockito.Mockito.mock(org.springframework.data.mongodb.gridfs.GridFsResource.class);
    }
}
