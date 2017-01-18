package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.scheduler.recovery.constrain.TimedLaunchConstrainer;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.PodSpec;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

import static org.mockito.Mockito.when;

/**
 * This class tests the TimedLaunchConstrainer class.
 */
public class TimedLaunchConstrainerTest {
    private static final Duration MIN_DELAY = Duration.ofMillis(3000);
    private TimedLaunchConstrainer timedLaunchConstrainer;

    @Mock private PodInstance podInstance;
    @Mock private PodSpec podSpec;

    private static class TestTimedLaunchConstrainer extends TimedLaunchConstrainer {
        private long currentTime;

        public TestTimedLaunchConstrainer(Duration minDelay) {
            super(minDelay);
        }

        public void setCurrentTime(long currentTime) {
            this.currentTime = currentTime;
        }

        @Override
        protected long getCurrentTimeMs() {
            return currentTime;
        }

    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(podSpec.isSticky()).thenReturn(true);
        when(podInstance.getPod()).thenReturn(podSpec);
        timedLaunchConstrainer = new TimedLaunchConstrainer(MIN_DELAY);
    }

    @Test
    public void testConstruction() {
        Assert.assertNotNull(timedLaunchConstrainer);
    }

    @Test
    public void testCanLaunchNoneAfterNoRecovery() {
        timedLaunchConstrainer.launchHappened(podInstance, null, RecoveryType.NONE);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(podInstance, RecoveryType.NONE));
    }

    @Test
    public void testCanLaunchTransientAfterNoRecovery() {
        timedLaunchConstrainer.launchHappened(podInstance, null, RecoveryType.NONE);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(podInstance, RecoveryType.TRANSIENT));
    }

    @Test
    public void testCanLaunchPermanentAfterNoRecovery() {
        timedLaunchConstrainer.launchHappened(podInstance, null, RecoveryType.NONE);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(podInstance, RecoveryType.PERMANENT));
    }

    @Test
    public void testCanLaunchNoneAfterTransientRecovery() {
        timedLaunchConstrainer.launchHappened(podInstance, null, RecoveryType.TRANSIENT);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(podInstance, RecoveryType.NONE));
    }

    @Test
    public void testCanLaunchPermanentAfterTransientRecovery() {
        timedLaunchConstrainer.launchHappened(podInstance, null, RecoveryType.TRANSIENT);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(podInstance, RecoveryType.PERMANENT));
    }

    @Test
    public void testCanLaunchTransientAfterTransientRecovery() {
        timedLaunchConstrainer.launchHappened(podInstance, null, RecoveryType.TRANSIENT);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(podInstance, RecoveryType.TRANSIENT));
    }

    @Test
    public void testCanLaunchNoneAfterPermanentRecovery() {
        timedLaunchConstrainer.launchHappened(podInstance, null, RecoveryType.PERMANENT);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(podInstance, RecoveryType.NONE));
    }

    @Test
    public void testCanLaunchTransientAfterPermanentRecovery() {
        timedLaunchConstrainer.launchHappened(podInstance, null, RecoveryType.PERMANENT);
        Assert.assertTrue(timedLaunchConstrainer.canLaunch(podInstance, RecoveryType.TRANSIENT));
    }

    @Test
    public void testCannotLaunchPermanentAfterPermanentRecovery() {
        timedLaunchConstrainer.launchHappened(podInstance, null, RecoveryType.PERMANENT);
        Assert.assertFalse(timedLaunchConstrainer.canLaunch(podInstance, RecoveryType.PERMANENT));
    }

    @Test
    public void testCanLaunchAfterPermanentRecoveryAndDelay() throws InterruptedException {
        TestTimedLaunchConstrainer testTimedLaunchConstrainer = new TestTimedLaunchConstrainer(MIN_DELAY);
        testTimedLaunchConstrainer.launchHappened(podInstance, null, RecoveryType.PERMANENT);
        testTimedLaunchConstrainer.setCurrentTime(System.currentTimeMillis());
        Assert.assertFalse(testTimedLaunchConstrainer.canLaunch(podInstance, RecoveryType.PERMANENT));
        testTimedLaunchConstrainer.setCurrentTime(testTimedLaunchConstrainer.getCurrentTimeMs() + MIN_DELAY.toMillis() * 1000 + 1);
        Assert.assertTrue(testTimedLaunchConstrainer.canLaunch(podInstance, RecoveryType.PERMANENT));
    }
}
