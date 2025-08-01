package com.example.tpv.data.api

import com.example.tpv.data.model.Pedido
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface PedidoApi {
    @POST("pedidos") // Ajusta esta ruta según tu API
    fun enviarPedido(@Body pedido: Pedido): Call<Void>
}
