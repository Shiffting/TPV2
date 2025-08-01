package com.example.tpv.viewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.tpv.data.api.RetrofitClient
import com.example.tpv.data.model.FamiliaProducto
import com.example.tpv.data.model.Producto
import com.example.tpv.data.model.Sala
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProductosViewModel : ViewModel() {
    private val _productos = MutableLiveData<List<Producto>>()
    val productos: LiveData<List<Producto>> = _productos

    private val _familias = MutableLiveData<List<FamiliaProducto>>()
    val familias: LiveData<List<FamiliaProducto>> = _familias

    private val _salas = MutableLiveData<List<Sala>>()
    val salas: LiveData<List<Sala>> = _salas

    fun cargarProductos(db:String, nombreLocal: String) {
        RetrofitClient.apiService.getProductos(db, nombreLocal).enqueue(object : Callback<List<Producto>> {
            override fun onResponse(call: Call<List<Producto>>, response: Response<List<Producto>>) {
                if (response.isSuccessful) {
                    _productos.value = response.body()
                }
            }

            override fun onFailure(call: Call<List<Producto>>, t: Throwable) {
                Log.e("API", "Error al obtener productos", t)
            }
        })
    }

    fun cargarFamilias(db:String, nombreLocal: String) {
        RetrofitClient.apiService.getFamiliasProducto(db, nombreLocal).enqueue(object : Callback<List<FamiliaProducto>> {
            override fun onResponse(call: Call<List<FamiliaProducto>>, response: Response<List<FamiliaProducto>>) {
                if (response.isSuccessful) {
                    _familias.value = response.body()
                }
            }

            override fun onFailure(call: Call<List<FamiliaProducto>>, t: Throwable) {
                Log.e("API", "Error al obtener familias", t)
            }
        })
    }

    fun cargarSalas(db:String, local:String) {
        RetrofitClient.apiService.getSalas(db, local).enqueue(object : Callback<List<Sala>> {
            override fun onResponse(call: Call<List<Sala>>, response: Response<List<Sala>>) {
                if (response.isSuccessful) {
                    val salasRecibidas = response.body()
                    _salas.value = salasRecibidas ?: emptyList()
                } else {
                    Log.e("SALAS_VM", "Error en respuesta API: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<List<Sala>>, t: Throwable) {
                Log.e("SALAS_VM", "Fallo en API", t)
            }
        })
    }

    fun obtenerTarifaPorPlu(plu: Int, tipoTarifa: Int): Double {
        // Buscamos el producto en la lista cargada
        val prod = productos.value?.find { it.Plu == plu }
            ?: return 0.0

        // Seleccionamos el campo String correspondiente
        val tarifaStr = when (tipoTarifa) {
            1  -> prod.Tarifa1
            11 -> prod.Tarifa11
            13 -> prod.Tarifa13
            14 -> prod.Tarifa14
            15 -> prod.Tarifa15
            else -> prod.Tarifa1
        }

        // Normalizamos coma → punto y parseamos
        return tarifaStr
            .replace(",", ".")
            .toDoubleOrNull()
            ?: 0.0
    }
}