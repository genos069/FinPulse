package in.expensetrackerapp.document.service;

import in.expensetrackerapp.common.exception.BadRequestException;
import in.expensetrackerapp.common.exception.ConflictException;
import in.expensetrackerapp.common.exception.PayloadTooLargeException;
import in.expensetrackerapp.common.exception.ResourceNotFoundException;
import in.expensetrackerapp.common.exception.UnsupportedFileTypeException;
import in.expensetrackerapp.document.dto.DocumentResponse;
import in.expensetrackerapp.document.model.DocumentStatus;
import in.expensetrackerapp.document.model.DocumentType;
import in.expensetrackerapp.document.model.UploadedDocument;
import in.expensetrackerapp.document.repository.DocumentRepository;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    static final int MAX_PAGE_SIZE = 100;

    private final DocumentRepository documentRepository;
    private final GridFsStorageService storageService;
    private final long maxFileSizeBytes;

    public DocumentService(
            DocumentRepository documentRepository,
            GridFsStorageService storageService,
            @Value("${spring.servlet.multipart.max-file-size:10MB}") String maxFileSize) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.maxFileSizeBytes = DataSize.parse(maxFileSize).toBytes();
    }

    public DocumentResponse upload(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("A non-empty file is required");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new PayloadTooLargeException("Uploaded file exceeds the maximum allowed size");
        }

        DocumentType type = detectType(file);

        String documentId = new ObjectId().toHexString();
        String storedFilename = UUID.randomUUID() + "." + type.getExtension();
        String gridFsFileId = null;
        try {
            MessageDigest digest = newSha256();
            try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
                gridFsFileId = storageService.store(in, storedFilename, type.getContentType(), userId, documentId);
            }

            Instant now = Instant.now();
            UploadedDocument doc = new UploadedDocument();
            doc.setId(documentId);
            doc.setUserId(userId);
            doc.setOriginalFilename(FilenameSanitizer.sanitize(file.getOriginalFilename()));
            doc.setStoredFilename(storedFilename);
            doc.setContentType(type.getContentType());
            doc.setSizeBytes(file.getSize());
            doc.setSha256(HexFormat.of().formatHex(digest.digest()));
            doc.setGridFsFileId(gridFsFileId);
            doc.setStatus(DocumentStatus.UPLOADED);
            doc.setCreatedAt(now);
            doc.setUpdatedAt(now);

            // insert() (not save()) so an id collision fails instead of silently overwriting.
            UploadedDocument saved = documentRepository.insert(doc);
            return DocumentResponse.fromDocument(saved);
        } catch (IOException e) {
            storageService.deleteQuietly(gridFsFileId);
            throw new BadRequestException("Could not read the uploaded file");
        } catch (RuntimeException e) {
            // Never leave an orphaned GridFS file behind when metadata persistence fails.
            storageService.deleteQuietly(gridFsFileId);
            throw e;
        }
    }

    public List<DocumentResponse> list(String userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return documentRepository
                .findByUserId(userId, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(DocumentResponse::fromDocument)
                .getContent();
    }

    public DocumentResponse get(String userId, String documentId) {
        return DocumentResponse.fromDocument(findOwned(userId, documentId));
    }

    public DocumentDownload download(String userId, String documentId) {
        UploadedDocument doc = findOwned(userId, documentId);
        Resource resource = storageService.open(doc.getGridFsFileId())
                .orElseThrow(() -> {
                    log.error("GridFS file missing for document {}", doc.getId());
                    return new ResourceNotFoundException("Document file is not available");
                });
        return new DocumentDownload(doc, resource);
    }

    public void delete(String userId, String documentId) {
        UploadedDocument doc = findOwned(userId, documentId);
        if (doc.getStatus() == DocumentStatus.PROCESSING) {
            throw new ConflictException("Document is currently being processed and cannot be deleted");
        }
        // Metadata first: a leftover file is a harmless orphan, a leftover record pointing at a missing file is not.
        documentRepository.deleteById(doc.getId());
        storageService.deleteQuietly(doc.getGridFsFileId());
    }

    /** Owner-scoped lookup. Another user's document is reported as 404 so its existence is not revealed. */
    private UploadedDocument findOwned(String userId, String documentId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    private DocumentType detectType(MultipartFile file) {
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(FileSignatureDetector.HEADER_LENGTH);
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file");
        }
        DocumentType detected = FileSignatureDetector.detect(header)
                .orElseThrow(() -> new UnsupportedFileTypeException(
                        "Unsupported file type. Only PDF, JPG and PNG files are accepted"));

        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT).trim();
        boolean declaredIsSupported = declared.equals("application/pdf") || declared.equals("image/jpeg")
                || declared.equals("image/jpg") || declared.equals("image/png");
        boolean matches = declared.equals(detected.getContentType())
                || (declared.equals("image/jpg") && detected == DocumentType.JPEG);
        if (declaredIsSupported && !matches) {
            throw new UnsupportedFileTypeException("File content does not match its declared type");
        }
        return detected;
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
