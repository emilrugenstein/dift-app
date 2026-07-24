package app.dift

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.dift.system.detect.OwnAppForegroundTracker
import app.dift.system.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DiftApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var workScheduler: WorkScheduler

    @Inject lateinit var ownAppForegroundTracker: OwnAppForegroundTracker

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ownAppForegroundTracker.register(this)
        workScheduler.ensureScheduled()
    }
}
