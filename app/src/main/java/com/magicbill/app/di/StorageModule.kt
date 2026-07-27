package com.magicbill.app.di

import android.content.Context
import androidx.room.Room
import com.magicbill.app.data.local.KvCacheDao
import com.magicbill.app.data.local.MagicBillDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MagicBillDatabase =
        Room.databaseBuilder(context, MagicBillDatabase::class.java, "magicbill.db")
            // Since v3 this DB holds the owner's offline mirror (bills_local &
            // co.) — real migrations are MANDATORY so an upgrade never wipes
            // it. The destructive fallback stays only as a last resort for
            // downgrades/corruption.
            .addMigrations(MagicBillDatabase.MIGRATION_3_4)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideKvCacheDao(db: MagicBillDatabase): KvCacheDao = db.kvCacheDao()

    @Provides
    fun provideOwnerLocalDao(db: MagicBillDatabase): com.magicbill.app.data.local.OwnerLocalDao =
        db.ownerLocalDao()

    @Provides
    fun provideOrdersLocalDao(db: MagicBillDatabase): com.magicbill.app.data.local.OrdersLocalDao =
        db.ordersLocalDao()
}
