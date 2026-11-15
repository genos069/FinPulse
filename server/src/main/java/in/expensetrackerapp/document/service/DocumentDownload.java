package in.expensetrackerapp.document.service;

import in.expensetrackerapp.document.model.UploadedDocument;
import org.springframework.core.io.Resource;

public record DocumentDownload(UploadedDocument document, Resource resource) {
}
