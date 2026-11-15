package in.expensetrackerapp.document.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GridFsStorageServiceTest {

    private static final String VALID_ID = "507f1f77bcf86cd799439011";

    @Mock
    private GridFsTemplate gridFsTemplate;

    @InjectMocks
    private GridFsStorageService storage;

    @Test
    void store_writesOwnerMetadataAndReturnsHexId() {
        ObjectId id = new ObjectId(VALID_ID);
        ArgumentCaptor<Object> metadata = ArgumentCaptor.forClass(Object.class);
        when(gridFsTemplate.store(any(InputStream.class), eq("f.pdf"), eq("application/pdf"), metadata.capture()))
                .thenReturn(id);

        String result = storage.store(new ByteArrayInputStream(new byte[]{1}), "f.pdf", "application/pdf", "user-1", "doc-1");

        assertThat(result).isEqualTo(VALID_ID);
        Document stored = (Document) metadata.getValue();
        assertThat(stored.getString("userId")).isEqualTo("user-1");
        assertThat(stored.getString("documentId")).isEqualTo("doc-1");
    }

    @Test
    void open_malformedIdReturnsEmptyWithoutQuerying() {
        assertThat(storage.open("not-an-object-id")).isEmpty();
        assertThat(storage.open(null)).isEmpty();
        verifyNoInteractions(gridFsTemplate);
    }

    @Test
    void open_unknownFileReturnsEmpty() {
        when(gridFsTemplate.findOne(any(Query.class))).thenReturn(null);

        assertThat(storage.open(VALID_ID)).isEmpty();
    }

    @Test
    void open_existingFileReturnsResource() {
        GridFSFile file = mock(GridFSFile.class);
        GridFsResource resource = mock(GridFsResource.class);
        when(gridFsTemplate.findOne(any(Query.class))).thenReturn(file);
        when(gridFsTemplate.getResource(file)).thenReturn(resource);

        assertThat(storage.open(VALID_ID)).containsSame(resource);
    }

    @Test
    void deleteQuietly_ignoresMalformedIds() {
        storage.deleteQuietly("../etc/passwd");
        storage.deleteQuietly(null);

        verifyNoInteractions(gridFsTemplate);
    }

    @Test
    void deleteQuietly_deletesByIdAndSwallowsFailures() {
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(gridFsTemplate).delete(any(Query.class));

        storage.deleteQuietly(VALID_ID);   // must not throw

        verify(gridFsTemplate).delete(any(Query.class));
    }
}
