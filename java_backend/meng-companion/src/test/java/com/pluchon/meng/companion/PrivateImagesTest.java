package com.pluchon.meng.companion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.companion.mapper.CompanionMapper;
import com.pluchon.meng.companion.service.PrivateImages;
import com.pluchon.meng.companion.service.PrivateImageStorage;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PrivateImagesTest {
    private final PrivateImages images = new PrivateImages();
    private final CompanionMapper mapper = mock(CompanionMapper.class);
    private final PrivateImageStorage storage = mock(PrivateImageStorage.class);
    private final MockMultipartFile file = new MockMultipartFile("file", new byte[]{1});

    @BeforeEach
    void setup() {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);
        ReflectionTestUtils.setField(images, "mapper", mapper);
        ReflectionTestUtils.setField(images, "storage", storage);
        ReflectionTestUtils.setField(images, "redis", redis);
        ReflectionTestUtils.setField(images, "clock", Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"), ZoneOffset.UTC));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clear() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test
    void pendingLimitRejectsBeforeWritingImage() {
        when(mapper.pendingImageCount(41)).thenReturn(20L);
        assertThrows(BusinessException.class, () -> images.upload(41, file));
        verify(mapper).lockBond(41, "NEKO");
        verifyNoInteractions(storage);
    }

    @Test
    void expiredFilesAreDeletedOnlyAfterDatabaseCommit() throws Exception {
        when(mapper.expiredPendingImages(eq(41L), any())).thenReturn(List.of("expired.png"));
        when(storage.save(any())).thenReturn(new PrivateImageStorage.Saved("new.png", ""));
        when(mapper.imageId(41, "new.png")).thenReturn(12L);
        assertEquals(12L, images.upload(41, file).get("id"));
        verify(mapper).expireImage(41, "expired.png");
        verify(storage, never()).delete(anyString());
        var sync = TransactionSynchronizationManager.getSynchronizations().get(0);
        sync.afterCommit();
        sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(storage).delete("expired.png");
        verify(storage, never()).delete("new.png");
    }

    @Test
    void rollbackRemovesNewFileAndPreservesExpiredFiles() throws Exception {
        when(mapper.expiredPendingImages(eq(41L), any())).thenReturn(List.of("expired.png"));
        when(storage.save(any())).thenReturn(new PrivateImageStorage.Saved("new.png", ""));
        when(mapper.imageId(41, "new.png")).thenReturn(12L);
        images.upload(41, file);
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage).delete("new.png");
        verify(storage, never()).delete("expired.png");
    }
}
