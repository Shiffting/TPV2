package com.example.tpv.viewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.tpv.data.api.RetrofitClient
import com.example.tpv.data.model.Local
import com.example.tpv.data.model.Terminal
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LocalesViewModel : ViewModel() {
    private val _locales = MutableLiveData<List<Local>>()
    val locales: LiveData<List<Local>> = _locales

    private val _localSeleccionado = MutableLiveData<Local?>()
    val localSeleccionado: LiveData<Local?> = _localSeleccionado

    private val _terminales = MutableLiveData<List<Terminal>>()
    val terminales: LiveData<List<Terminal>> = _terminales

    fun cargarLocales(db:String) {
        RetrofitClient.apiService.getLocales(db)
            .enqueue(object : Callback<List<Local>> {
                override fun onResponse(
                    call: Call<List<Local>>,
                    response: Response<List<Local>>
                ) {
                    if (response.isSuccessful) {
                        _locales.value = response.body()
                    } else {
                        Log.e("LocalesVM", "Error en respuesta API: ${response.code()}")
                        Log.e("LOCALESVM", "empresa: $locales")
                    }
                }

                override fun onFailure(call: Call<List<Local>>, t: Throwable) {
                    Log.e("LOCALESVM", "Fallo al cargar locales", t)
                }
            })
    }

    fun seleccionarLocalPorIdTexto(db:String, texto: String) {
        val id = texto.toIntOrNull()
        val local = _locales.value?.find { it.id_local == id }
        _localSeleccionado.value = local
        if (local != null) {
            cargarTerminales(db, local.id_local)
        } else {
            _terminales.value = emptyList()
        }
    }

    private fun cargarTerminales(db:String, idLocal: Int) {
        Log.d("TERMINALES_VM", "Cargando terminales para id_local: $idLocal")
        RetrofitClient.apiService.getTerminalesPorLocal(db, idLocal)
            .enqueue(object : Callback<List<Terminal>> {
                override fun onResponse(call: Call<List<Terminal>>, response: Response<List<Terminal>>) {
                    if (response.isSuccessful) {
                        Log.d("TERMINALES_VM", "Terminales recibidos: ${response.body()?.size}")

                        _terminales.value = response.body() ?: emptyList()
                    } else {
                        Log.e("TERMINALES_VM", "Error en respuesta API: ${response.code()}")
                        Log.e("TERMINALES_VM", "Mensaje: ${response.errorBody()?.string()}")
                        _terminales.value = emptyList()
                    }
                }

                override fun onFailure(call: Call<List<Terminal>>, t: Throwable) {
                    _terminales.value = emptyList()
                    Log.e("LocalesVM", "Error al cargar terminales", t)
                }
            })
    }
}