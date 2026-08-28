package com.magicbill.app.di

import android.content.Context
import com.magicbill.app.BuildConfig
import com.magicbill.app.cloud.CloudLink
import com.magicbill.app.cloud.SessionStore
import com.magicbill.app.core.Clock
import com.magicbill.app.counter.CounterLink
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.prefs.KeyBox
import com.magicbill.app.prefs.Secure
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Work that outlives a screen: the mirror pull, the intent sender, the stream. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
abstract class BindsModule {
    @Binds abstract fun keyBox(secure: Secure): KeyBox
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun clock(): Clock = Clock.system

    @Provides @Singleton @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun cloudLink(sessions: SessionStore, clock: Clock): CloudLink =
        CloudLink(BuildConfig.CLOUD_URL.trimEnd('/'), BuildConfig.CLOUD_ANON_KEY, CloudLink.client(), sessions, clock)

    @Provides @Singleton fun counterLink(): CounterLink = CounterLink()

    @Provides @Singleton
    fun mirror(cloud: CloudLink, db: MbDatabase, clock: Clock): com.magicbill.app.cloud.Mirror = com.magicbill.app.cloud.Mirror(cloud, db, clock)

    @Provides @Singleton fun database(@ApplicationContext context: Context): MbDatabase = MbDatabase.open(context)
}
