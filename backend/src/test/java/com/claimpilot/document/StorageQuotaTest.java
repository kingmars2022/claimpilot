package com.claimpilot.document;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

class StorageQuotaTest {

    @Test
    void uploadsFitWithinTheLimits() {
        assertThatNoException()
                .isThrownBy(() -> DocumentService.checkQuota(99, 400_000_000, 50_000_000, 100, 500_000_000));
    }

    @Test
    void theFileCountIsLimited() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DocumentService.checkQuota(100, 0, 1, 100, 500_000_000))
                .withMessageContaining("limit of 100 files");
    }

    @Test
    void theTotalSizeIsLimited() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DocumentService.checkQuota(3, 480_000_000, 30_000_000, 100, 500_000_000))
                .withMessageContaining("500 MB");
    }
}
