package com.example.data.local.database

import androidx.room.TypeConverter
import com.example.core.model.DamanStyle
import com.example.core.model.DeviceRegistrationStatus
import com.example.core.model.NeckStyle
import com.example.core.model.OrderStatus
import com.example.core.model.PaymentMethod
import com.example.core.model.PaymentStatus
import com.example.core.model.PocketStyle
import com.example.core.model.PocketStyleCodec
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.SyncEntityType
import com.example.core.model.SyncOperation
import com.example.core.model.SyncState
import com.example.core.model.SyncStatus

class DarziConverters {
  @TypeConverter fun fromNeckStyle(value: NeckStyle): String = value.name
  @TypeConverter fun toNeckStyle(value: String): NeckStyle = NeckStyle.valueOf(value)

  @TypeConverter fun fromDamanStyle(value: DamanStyle): String = value.name
  @TypeConverter fun toDamanStyle(value: String): DamanStyle = DamanStyle.valueOf(value)

  @TypeConverter fun fromPocketStyle(value: PocketStyle): String = value.name
  @TypeConverter fun toPocketStyle(value: String): PocketStyle = PocketStyle.valueOf(value)

  @TypeConverter fun fromPocketStyleSet(value: Set<PocketStyle>): String = PocketStyleCodec.encode(value)
  @TypeConverter fun toPocketStyleSet(value: String): Set<PocketStyle> = PocketStyleCodec.decode(value)

  @TypeConverter fun fromSleeveStyle(value: SleeveStyle): String = value.name
  @TypeConverter fun toSleeveStyle(value: String): SleeveStyle = SleeveStyle.valueOf(value)

  @TypeConverter fun fromSilaiStyle(value: SilaiStyle): String = value.name
  @TypeConverter fun toSilaiStyle(value: String): SilaiStyle = SilaiStyle.valueOf(value)

  @TypeConverter fun fromOrderStatus(value: OrderStatus): String = value.name
  @TypeConverter fun toOrderStatus(value: String): OrderStatus = OrderStatus.valueOf(value)

  @TypeConverter fun fromPaymentStatus(value: PaymentStatus): String = value.name
  @TypeConverter fun toPaymentStatus(value: String): PaymentStatus = PaymentStatus.valueOf(value)

  @TypeConverter fun fromPaymentMethod(value: PaymentMethod): String = value.name
  @TypeConverter fun toPaymentMethod(value: String): PaymentMethod = PaymentMethod.valueOf(value)

  @TypeConverter fun fromSyncEntityType(value: SyncEntityType): String = value.name
  @TypeConverter fun toSyncEntityType(value: String): SyncEntityType = SyncEntityType.valueOf(value)

  @TypeConverter fun fromSyncOperation(value: SyncOperation): String = value.name
  @TypeConverter fun toSyncOperation(value: String): SyncOperation = SyncOperation.valueOf(value)

  @TypeConverter fun fromSyncStatus(value: SyncStatus): String = value.name
  @TypeConverter fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

  @TypeConverter fun fromSyncState(value: SyncState): String = value.name
  @TypeConverter fun toSyncState(value: String): SyncState = SyncState.valueOf(value)

  @TypeConverter fun fromDeviceRegistrationStatus(value: DeviceRegistrationStatus): String = value.name
  @TypeConverter fun toDeviceRegistrationStatus(value: String): DeviceRegistrationStatus =
    DeviceRegistrationStatus.valueOf(value)
}
