package pl.syntaxdevteam.medstock.core.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveBackupSchedulerTest {

    @Test
    fun missingBackupIsOverdue() {
        assertTrue(DriveBackupScheduler.isBackupOverdue(null, nowEpochMillis = 1_000L))
    }

    @Test
    fun backupBecomesOverdueAfterTwentyFourHours() {
        val dayMillis = 24L * 60L * 60L * 1000L

        assertTrue(DriveBackupScheduler.isBackupOverdue(1_000L, nowEpochMillis = 1_000L + dayMillis))
    }

    @Test
    fun recentBackupIsNotOverdue() {
        val dayMillis = 24L * 60L * 60L * 1000L

        assertFalse(DriveBackupScheduler.isBackupOverdue(1_000L, nowEpochMillis = dayMillis))
    }
}
