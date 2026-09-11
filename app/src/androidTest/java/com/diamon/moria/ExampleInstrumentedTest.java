package com.diamon.moria;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;

import com.diamon.moria.ui.activities.MainActivity;
import com.diamon.moria.utils.AssetHelper;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.diamon.moria", appContext.getPackageName());
    }

    @Test
    public void testActivityLaunchAndRuntimeReady() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity);
                assertTrue(AssetHelper.ensureRuntimeReady(activity.getApplicationContext()));
            });
        }
    }
}
