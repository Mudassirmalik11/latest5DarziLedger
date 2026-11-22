package com.example.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class NeckStyle(val displayName: String) {
  COLLAR("Collar"),
  BAN("Ban"),
  HALF_BAN("Half-Ban"),
  ROUND("Round"),
}

@Serializable
enum class DamanStyle(val displayName: String) {
  GOL("Gol (Round)"),
  CHORASI("Chorasi (Square)"),
}

@Serializable
enum class PocketStyle(val displayName: String) {
  FRONT("Front Pocket"),
  ONE_SIDE("1 Side"),
  TWO_SIDE("2 Side"),
  SHALWAR_POCKET("Shalwar Pocket"),
}

object PocketStyleCodec {
  fun ordered(styles: Collection<PocketStyle>): List<PocketStyle> =
    PocketStyle.entries.filter { it in styles }

  fun encode(styles: Collection<PocketStyle>): String =
    ordered(styles).joinToString(",") { it.name }

  fun decode(raw: String?): Set<PocketStyle> {
    if (raw.isNullOrBlank()) return emptySet()
    return raw.split(',')
      .mapNotNull { token ->
        val trimmed = token.trim()
        PocketStyle.entries.firstOrNull { it.name == trimmed }
      }
      .toSet()
  }
}

object PocketStyleSetSerializer : kotlinx.serialization.KSerializer<Set<PocketStyle>> {
  override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor =
    kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
      "PocketStyleSet",
      kotlinx.serialization.descriptors.PrimitiveKind.STRING,
    )

  override fun serialize(
    encoder: kotlinx.serialization.encoding.Encoder,
    value: Set<PocketStyle>,
  ) {
    encoder.encodeString(PocketStyleCodec.encode(value))
  }

  override fun deserialize(
    decoder: kotlinx.serialization.encoding.Decoder,
  ): Set<PocketStyle> = PocketStyleCodec.decode(decoder.decodeString())
}

@Serializable
enum class SleeveStyle(val displayName: String) {
  PLAIN("Plain"),
  SINGLE_CUFF("Single Cuff"),
  DOUBLE_CUFF("Double Cuff"),
  STUDS("Studs"),
}

@Serializable
enum class SilaiStyle(val displayName: String) {
  SINGLE("Single"),
  DOUBLE("Double"),
  CHAMAK_DHAGA("Chamak Dhaga"),
}

@Serializable
enum class OrderStatus(val displayName: String) {
  PENDING("Pending"),
  READY("Ready"),
  DELIVERED("Delivered"),
  CANCELLED("Cancelled");

  fun allowedNextStatuses(): Set<OrderStatus> = when (this) {
    PENDING -> setOf(READY, CANCELLED)
    READY -> setOf(PENDING, DELIVERED, CANCELLED)
    DELIVERED -> emptySet()
    CANCELLED -> emptySet()
  }

  fun canTransitionTo(target: OrderStatus): Boolean =
    this == target || target in allowedNextStatuses()
}

@Serializable
enum class PaymentStatus(val displayName: String) {
  UNPAID("Unpaid"),
  PARTIAL("Partial"),
  PAID("Paid"),
}

@Serializable
enum class PaymentMethod(val displayName: String) {
  CASH("Cash"),
  JAZZCASH("JazzCash"),
  EASYPAISA("EasyPaisa"),
  BANK_TRANSFER("Bank Transfer"),
  OTHER("Other"),
}

@Serializable
enum class SyncEntityType {
  SHOP,
  CUSTOMER,
  MEASUREMENT,
  ORDER,
  PAYMENT,
  DEVICE_CONFIG,
  ORDER_RESERVATION,
}

@Serializable
enum class SyncOperation {
  CREATE,
  UPDATE,
  DELETE,
}

@Serializable
enum class SyncStatus {
  PENDING,
  IN_PROGRESS,
  SYNCED,
  FAILED,
  CONFLICT,
}

@Serializable
enum class SyncState {
  IDLE,
  SYNCING,
  ERROR,
  OFFLINE,
}

@Serializable
enum class DeviceRegistrationStatus {
  UNREGISTERED,
  PENDING,
  AUTHORIZED,
  REVOKED,
}

@Serializable
enum class ShopUserRole(val displayName: String) {
  OWNER("Shop Owner"),
  STAFF("Staff User");

  companion object {
    fun fromString(raw: String?): ShopUserRole =
      entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: OWNER
  }
}
