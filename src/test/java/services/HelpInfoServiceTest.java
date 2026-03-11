package services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpInfoServiceTest {

    @Test
    void getHelpInfo_containsMainCommands() {
        HelpInfoService service = new HelpInfoService(null);
        String text = service.getHelpInfo();
        assertTrue(text.contains("+акция"));
        assertTrue(text.contains("+валюта"));
        assertTrue(text.contains("+помощь"));
    }
}
