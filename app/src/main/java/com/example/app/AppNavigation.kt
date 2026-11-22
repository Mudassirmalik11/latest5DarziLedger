package com.example.app

import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

@Serializable
data object CustomersRoute

@Serializable
data object OrdersRoute

@Serializable
data object LedgerRoute

@Serializable
data object SettingsRoute

@Serializable
data class CustomerDetailRoute(val customerId: String)

@Serializable
data class MeasurementEditorRoute(
  val customerId: String,
  val sourceMeasurementId: String? = null,
)

@Serializable
data class CreateOrderRoute(val customerId: String? = null)

@Serializable
data class OrderDetailRoute(val orderId: String)

@Serializable
data object SyncStatusRoute
