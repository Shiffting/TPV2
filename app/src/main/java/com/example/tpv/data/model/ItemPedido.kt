package com.example.tpv.data.model

data class ItemPedido(
    val nombreBase: String,
    var nombre: String,
    var precio: Double,
    var cantidad: Int = 1,
    val plu: Int,
    val familia: String,
    val consumoSolo: String,
    val impresora: String,
    val ivaVenta: String,
    val pluadbc: Int,
    var propiedades: MutableList<String> = mutableListOf(),
    val isHeader: Boolean = false,
    var tarifaUsada:String = "Tarifa1",
    var yaIntroducido: Boolean = false,
    var introducidas: Int = 0, // cuántas ya hemos enviado a la BD
    var propsIntroducidas: Int = 0
)
