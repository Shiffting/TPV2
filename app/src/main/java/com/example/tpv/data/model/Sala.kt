package com.example.tpv.data.model

data class Sala(
    val denominacion: String,
    val tarifaPredet: String,
    val numMesas: Int,
    val esp1: Int,
    val mesas: List<String>,
    val gestionaMesas: Int
)