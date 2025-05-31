package ru.drsn.waves.data.datasource.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.drsn.waves.data.datasource.local.db.dao.ChatSessionDao
import ru.drsn.waves.data.datasource.local.db.dao.MessageDao
import ru.drsn.waves.data.datasource.local.db.entity.ChatSessionEntity
import ru.drsn.waves.data.datasource.local.db.entity.MessageEntity
import ru.drsn.waves.data.datasource.local.db.entity.StringListConverter

@Database(
    entities = [ChatSessionEntity::class, MessageEntity::class],
    version = 1, // Увеличивай версию при изменении схемы и добавляй миграции
    exportSchema = false // Отключи экспорт схемы в JSON, если она не нужна для тестов или анализа
)
@TypeConverters(StringListConverter::class) // Регистрируем наш конвертер на уровне БД
abstract class AppDatabase : RoomDatabase() {

    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun messageDao(): MessageDao

    companion object {
        // Ключевое слово volatile гарантирует, что значение INSTANCE всегда актуально
        // и одинаково для всех потоков выполнения.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DATABASE_NAME = "waves_chat_database"

        fun getInstance(context: Context): AppDatabase {
            // synchronized гарантирует, что только один поток может одновременно выполнять этот блок кода,
            // предотвращая создание нескольких экземпляров базы данных.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    // .fallbackToDestructiveMigration() // В разработке: удаляет и пересоздает БД при изменении версии без миграции. НЕ ИСПОЛЬЗОВАТЬ В ПРОДАШЕНЕ!
                    // .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // Здесь будут твои миграции
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}