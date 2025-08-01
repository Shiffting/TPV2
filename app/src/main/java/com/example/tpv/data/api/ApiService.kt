package com.example.tpv.data.api

import com.example.tpv.data.model.Empleado
import com.example.tpv.data.model.FamiliaProducto
import com.example.tpv.data.model.Local
import com.example.tpv.data.model.Pedido
import com.example.tpv.data.model.Producto
import com.example.tpv.data.model.Propiedades
import com.example.tpv.data.model.Terminal
import com.example.tpv.data.model.Sala
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("productos")
    fun getProductos(
        @Header("X-Database-ID") dbId: String,
        @Query("colorNet") colorNet: String
    ): Call<List<Producto>>

    @GET("familias")
    fun getFamiliasProducto(
        @Header("X-Database-ID") dbId: String,
        @Query("colorNet") colorNet: String
    ): Call<List<FamiliaProducto>>

    @GET("salasymesas")
    fun getSalas(
        @Header("X-Database-ID") dbId: String,
        @Query("colorNet") colorNet: String
    ): Call<List<Sala>>

    @GET("empleados/{id_local}")
    fun cargarEmpleados(
        @Header("X-Database-ID") dbId: String,
        @Path("id_local") idLocal: Int
    ): Call<List<Empleado>>

    @GET("locales")
    fun getLocales(
        @Header("X-Database-ID") dbId: String
    ): Call<List<Local>>

    @GET("terminales/{id_local}")
    fun getTerminalesPorLocal(
        @Header("X-Database-ID") dbId: String,
        @Path("id_local") idLocal: Int
    ): Call<List<Terminal>>

    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("pedidos")
    fun enviarPedido(
        @Header("X-Database-ID") dbId: String,
        @Body pedido: Pedido
    ): Call<Void>

    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("pedidos/sincronizar/{reg}")
    fun sincronizarPedido(
        @Header("X-Database-ID") dbId: String,
        @Path("reg") reg: String,
        @Body producto: Pedido
    ): Call<Void>
    @Headers("Content-Type: application/json", "Accept: application/json")
    @DELETE("pedidos/{reg}/{plu}")
    fun borrarPedido(
        @Header("X-Database-ID") dbId: String,
        @Path("reg")    reg: String,
        @Path("plu")    plu: Int
    ): Call<Void>

    @GET("pedidos/pendientes")
    fun getPedidosPendientes(
        @Header("X-Database-ID") dbId: String,
        @Query("colorNet") colorNet: String
    ): Call<List<Pedido>>

    @GET("propiedades")
    fun getPropiedades(
        @Header("X-Database-ID") dbId: String,
        @Query("familia") familia: String,
        @Query("nombre") nombre: String,
        @Query("colorNet") colorNet: String
    ): Call<List<Propiedades>>

}

