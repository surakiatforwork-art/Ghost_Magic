package com.ghostmagic.android;

import android.os.Build;
import android.test.InstrumentationTestCase;
import com.ghostmagic.core.ActionException;
import com.ghostmagic.core.Cancellation;
import java.util.concurrent.atomic.AtomicReference;

/** Negative paths only: never enables Accessibility or captures a screen. */
public final class OcrScreenshotIntegrationTest extends InstrumentationTestCase {
    public void testUnconnectedServiceFailsWithTypedPermissionError()throws Exception {
        if(OcrScreenshotService.isConnected())return; // A user-enabled service must not be disabled by a test.
        try{OcrScreenshotService.capture(Cancellation.NEVER);fail("Expected unavailable capture");}
        catch(ActionException error){assertEquals(Build.VERSION.SDK_INT<30?ActionException.UNSUPPORTED:ActionException.PERMISSION_REQUIRED,error.code());}
    }
    public void testCaptureNeverBlocksMainThread()throws Exception {
        AtomicReference<ActionException> failure=new AtomicReference<>();
        getInstrumentation().runOnMainSync(()->{try{OcrScreenshotService.capture(Cancellation.NEVER);}catch(ActionException error){failure.set(error);}});
        assertNotNull(failure.get());
        assertEquals(Build.VERSION.SDK_INT<30?ActionException.UNSUPPORTED:ActionException.EXECUTION_FAILED,failure.get().code());
    }
}
