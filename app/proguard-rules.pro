# WorkManager instantiates workers by class name via the default WorkerFactory
# (reflection). R8 can't see that call, so keep the Worker's constructor.
-keep class dev.goutham.wallbreaker.SyncWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Room, WorkManager, and Compose all ship their own consumer ProGuard rules, so
# no further keeps are needed for the database, the sync scheduler, or the UI.
