package services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SharesInfoServiceTest {

    @Test
    void constructor_createsService() {
        SharesInfoService service = new SharesInfoService(null);
        assertNotNull(service);
    }
}
