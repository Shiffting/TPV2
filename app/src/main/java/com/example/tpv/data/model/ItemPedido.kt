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
    val pluadbc: String,
    var propiedades: MutableList<String> = mutableListOf()
)
