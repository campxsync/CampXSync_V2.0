package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.GlobalSetting;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link InMemoryGlobalSettingRepository} and contract verification.
 */
public class PostgresGlobalSettingRepositoryTest {

    private InMemoryGlobalSettingRepository repository;
    private UserSecurityContext context;

    @Before
    public void setUp() {
        repository = new InMemoryGlobalSettingRepository();
        context = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    public void testSaveAndRetrieveSetting() {
        GlobalSetting setting = new GlobalSetting();
        setting.setKey("platform.maintenance.window");
        setting.setValue("SUNDAY_0200_UTC");
        setting.setScope("INSTITUTE");

        GlobalSetting saved = repository.saveSetting(context, setting);
        assertEquals("platform.maintenance.window", saved.getKey());

        Optional<GlobalSetting> fetched = repository.getSettingByKey(context, "platform.maintenance.window");
        assertTrue(fetched.isPresent());
        assertEquals("SUNDAY_0200_UTC", fetched.get().getValue());
    }

    @Test
    public void testListSettings() {
        GlobalSetting s1 = new GlobalSetting();
        s1.setKey("auth.session.timeout.seconds");
        s1.setValue("3600");
        repository.saveSetting(context, s1);

        GlobalSetting s2 = new GlobalSetting();
        s2.setKey("billing.currency.default");
        s2.setValue("INR");
        repository.saveSetting(context, s2);

        List<GlobalSetting> list = repository.listSettings(context);
        assertEquals(2, list.size());
    }
}
