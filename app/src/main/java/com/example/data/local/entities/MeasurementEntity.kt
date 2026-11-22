package com.example.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.model.DamanStyle
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.PocketStyle
import com.example.core.model.PocketStyleCodec
import com.example.core.model.PocketStyleSetSerializer
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.StyleSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(
  tableName = "measurements",
  foreignKeys = [
    ForeignKey(
      entity = CustomerEntity::class,
      parentColumns = ["customerId"],
      childColumns = ["customerId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [
    Index(value = ["customerId"]),
    Index(value = ["customerId", "isCurrent"]),
  ],
)
data class MeasurementEntity(
  @PrimaryKey val measurementId: String,
  val customerId: String,
  val label: String = "Standard",
  val isCurrent: Boolean = true,
  // Kameez measurements in inches
  val kameezLambai: Double,
  val tera: Double,
  val bazu: Double,
  val chati: Double,
  val kamar: Double,
  val gheraDaman: Double,
  val galaCollar: Double,
  val cuff: Double,
  // Shalwar measurements in inches
  val shalwarLambai: Double,
  val paincha: Double,
  val gheraAsan: Double,
  // Style preferences
  val neckStyle: NeckStyle,
  val damanStyle: DamanStyle,
  @Serializable(with = PocketStyleSetSerializer::class)
  @SerialName("pocketStyle")
  @ColumnInfo(name = "pocketStyle")
  val pocketStyles: Set<PocketStyle> = emptySet(),
  val sleeveStyle: SleeveStyle,
  val silaiStyle: SilaiStyle,
  val notes: String = "",
  val createdAt: Long,
  val updatedAt: Long,
  val version: Long = 1L,
  val lastModifiedByDevice: String,
  val isDeleted: Boolean = false,
) {
  fun toMeasurementSnapshot(): MeasurementSnapshot = MeasurementSnapshot(
    sourceMeasurementId = measurementId,
    kameezLambai = kameezLambai,
    tera = tera,
    bazu = bazu,
    chati = chati,
    kamar = kamar,
    gheraDaman = gheraDaman,
    galaCollar = galaCollar,
    cuff = cuff,
    shalwarLambai = shalwarLambai,
    paincha = paincha,
    gheraAsan = gheraAsan,
    notes = notes,
  )

  fun toStyleSnapshot(): StyleSnapshot {
    val orderedPockets = PocketStyleCodec.ordered(pocketStyles)
    return StyleSnapshot(
      neckStyle = neckStyle,
      damanStyle = damanStyle,
      pocketStyle = orderedPockets.firstOrNull(),
      pocketStyles = orderedPockets,
      sleeveStyle = sleeveStyle,
      silaiStyle = silaiStyle,
    )
  }
}
