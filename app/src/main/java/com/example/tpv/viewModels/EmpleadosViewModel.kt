package com.example.tpv.viewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.tpv.data.api.RetrofitClient
import com.example.tpv.data.model.Empleado
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class EmpleadosViewModel : ViewModel() {
    private val _empleados = MutableLiveData<List<Empleado>>()
    val empleados: LiveData<List<Empleado>> = _empleados

    fun cargarEmpleados(db:String, idLocal: Int) {
        RetrofitClient.apiService.cargarEmpleados(db, idLocal)
            .enqueue(object : Callback<List<Empleado>> {
                override fun onResponse(
                    call: Call<List<Empleado>>,
                    response: Response<List<Empleado>>
                ) {
                    if (response.isSuccessful) {
                        _empleados.value = response.body()
                    } else {
                        Log.e("EmpleadosVM", "Error en respuesta: ${response.code()}")
                        _empleados.value = emptyList()
                    }
                }

                override fun onFailure(call: Call<List<Empleado>>, t: Throwable) {
                    Log.e("EmpleadosVM", "Fallo al cargar empleados", t)
                }
            })
    }
}
