package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.daos.CustomerDao
import com.example.data.local.daos.DeviceSyncDao
import com.example.data.local.daos.MeasurementDao
import com.example.data.local.daos.OrderDao
import com.example.data.local.daos.PaymentDao
import com.example.data.local.daos.ShopDao
import com.example.data.local.daos.SyncOutboxDao
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.entities.DeviceConfigEntity
import com.example.data.local.entities.MeasurementEntity
import com.example.data.local.entities.OrderEntity
import com.example.data.local.entities.OrderNumberReservationEntity
import com.example.data.local.entities.PaymentEntity
import com.example.data.local.entities.PrinterConfigEntity
import com.example.data.local.entities.ShopEntity
import com.example.data.local.entities.SyncConflictEntity
import com.example.data.local.entities.SyncMetadataEntity
import com.example.data.local.entities.SyncOutboxEntity

@Database(
  entities = [
    ShopEntity::class,
    CustomerEntity::class,
    MeasurementEntity::class,
    OrderEntity::class,
    PaymentEntity::class,
    SyncOutboxEntity::class,
    SyncMetadataEntity::class,
    DeviceConfigEntity::class,
    OrderNumberReservationEntity::class,
    PrinterConfigEntity::class,
    SyncConflictEntity::class,
  ],
  version = 4,
  exportSchema = true,
)
@TypeConverters(DarziConverters::class)
abstract class DarziDatabase : RoomDatabase() {
  abstract fun shopDao(): ShopDao
  abstract fun customerDao(): CustomerDao
  abstract fun measurementDao(): MeasurementDao
  abstract fun orderDao(): OrderDao
  abstract fun paymentDao(): PaymentDao
  abstract fun syncOutboxDao(): SyncOutboxDao
  abstract fun deviceSyncDao(): DeviceSyncDao

  companion object {
    const val DATABASE_NAME = "darzi_ledger.db"

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `sync_conflicts` (
            `conflictId` TEXT NOT NULL,
            `entityType` TEXT NOT NULL,
            `entityId` TEXT NOT NULL,
            `localPayloadJson` TEXT NOT NULL,
            `remotePayloadJson` TEXT NOT NULL,
            `localVersion` INTEGER NOT NULL,
            `remoteVersion` INTEGER NOT NULL,
            `detectedAt` INTEGER NOT NULL,
            `resolved` INTEGER NOT NULL,
            `resolvedAt` INTEGER,
            PRIMARY KEY(`conflictId`)
          )
          """.trimIndent(),
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_sync_conflicts_resolved` ON `sync_conflicts` (`resolved`)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS `index_sync_conflicts_entityType_entityId` ON `sync_conflicts` (`entityType`, `entityId`)"
        )
      }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "ALTER TABLE `customers` ADD COLUMN `customId` TEXT NOT NULL DEFAULT ''"
        )
      }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "ALTER TABLE `device_config` ADD COLUMN `userUid` TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
          "ALTER TABLE `device_config` ADD COLUMN `userRole` TEXT NOT NULL DEFAULT 'OWNER'"
        )
      }
    }

    @Volatile
    private var INSTANCE: DarziDatabase? = null

    fun getInstance(context: Context): DarziDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          DarziDatabase::class.java,
          DATABASE_NAME,
        )
          .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
          .build()
          .also { INSTANCE = it }
      }
    }
  }
}
