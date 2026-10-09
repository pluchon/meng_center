package com.pluchon.meng.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.service.security.ContactService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PhoneRegionTest {

    @Test
    void taiwanPrefixCannotBeMistakenForMainland() {
        UserProperties properties = mock(UserProperties.class);
        when(properties.phonePatterns()).thenReturn(Map.of("+86", "1[3-9][0-9]{9}", "+886", "9[0-9]{8}"));
        var contacts = new ContactService(properties, null);
        assertEquals("+886", contacts.countryCode("+886988776654"));
        assertEquals("+86", contacts.countryCode("+8613998877665"));
        assertEquals("+886988776654", contacts.normalizePhone("+886", "0988 776 654"));
        assertThrows(RuntimeException.class, () -> contacts.normalizePhone("+999", "invalid"));
    }
}
