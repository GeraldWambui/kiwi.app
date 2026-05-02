package co.juneai.june.di

import android.content.Context
import androidx.work.WorkManager
import co.juneai.june.ml.clip.ClipEmbedder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.objectbox.BoxStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ObjectBoxModule {



    // ── ML ────────────────────────────────────────────────────────────────────

    /**
     * [ClipEmbedder] is a singleton because loading two TFLite models (~150 MB combined)
     * is expensive. A single instance is safe: [ClipEmbedder] is serialized by the worker
     * coroutine and never accessed concurrently.
     *
     * The [ClipEmbedder.close] lifecycle is managed by the process; Hilt will not call it
     * automatically. If you need explicit teardown, observe [ProcessLifecycleOwner].
     */

    // ── Repository ────────────────────────────────────────────────────────────


    // ── WorkManager ───────────────────────────────────────────────────────────

    /**
     * WorkManager is internally a singleton. This binding allows Hilt to inject it
     * anywhere without a Context reference leaking into ViewModels or Workers.
     *
     * Not @Singleton here because [WorkManager.getInstance] is already idempotent;
     * calling it multiple times is safe and returns the same instance.
     */
    @Provides
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
