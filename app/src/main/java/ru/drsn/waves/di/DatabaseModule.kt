package ru.drsn.waves.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.drsn.waves.data.datasource.local.db.AppDatabase
import ru.drsn.waves.data.datasource.local.db.dao.ChatSessionDao
import ru.drsn.waves.data.datasource.local.db.dao.MessageDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext appContext: Context): AppDatabase {
        return AppDatabase.getInstance(appContext)
    }

    @Provides
    @Singleton
    fun provideChatSessionDao(appDatabase: AppDatabase): ChatSessionDao {
        return appDatabase.chatSessionDao()
    }

    @Provides
    @Singleton
    fun provideMessageDao(appDatabase: AppDatabase): MessageDao {
        return appDatabase.messageDao()
    }
}