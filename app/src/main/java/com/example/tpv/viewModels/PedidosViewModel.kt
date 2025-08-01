package com.example.tpv.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.tpv.data.api.RetrofitClient
import com.example.tpv.data.model.ItemPedido
import com.example.tpv.data.model.Pedido
import es.redsys.paysys.Utils.Log
import retrofit2.Call
import retrofit2.Response
import retrofit2.Callback
import java.util.UUID

class PedidoViewModel : ViewModel() {
    private val _mesaSeleccionada = MutableLiveData<String>("sin mesa")
    val mesaSeleccionada: LiveData<String> = _mesaSeleccionada
    private val _salaSeleccionada = MutableLiveData<String?>()
    val salaSeleccionada: LiveData<String?> get() = _salaSeleccionada
    private val _idPedidoPorMesa = MutableLiveData<MutableMap<String, String>>(mutableMapOf())
    private val _itemsPorMesa = MutableLiveData<MutableMap<String, MutableList<ItemPedido>>>(mutableMapOf())
    val itemsPorMesa: LiveData<MutableMap<String, MutableList<ItemPedido>>> = _itemsPorMesa

    fun añadirItem(item: ItemPedido) {
        val clave = claveSalaMesa() ?: return
        val mapa = _itemsPorMesa.value ?: mutableMapOf()
        val lista = mapa.getOrPut(clave) { mutableListOf() }
        // Busca un item “igual” en la lista (producto, precio y mismas propiedades)
        val existente = lista.find {
                it.nombre == item.nombre &&
                it.propiedades == item.propiedades &&
                it.precio == item.precio }
        if (existente != null) {
            // si ya hay uno idéntico: incrementa cantidad
            existente.cantidad += 1
        } else {
            // si no, añade como línea nueva
            lista.add(item)
        }
        _itemsPorMesa.value = mapa
    }
    fun reducirItem(item: ItemPedido, count: Int) {
        val clave = claveSalaMesa() ?: return
        val mapa  = _itemsPorMesa.value ?: return
        val lista = mapa[clave] ?: return

        // Busca ítem idéntico: mismo nombre, precio y propiedades
        val idx = lista.indexOf(item)
        if (idx >= 0) {
            val existente = lista[idx]
            if (existente.cantidad > count) {
                existente.cantidad -= count
            } else {
                lista.removeAt(idx)
            }
            _itemsPorMesa.value = mapa
        }
    }
    fun obtenerItems(mesa: String, sala: String?): List<ItemPedido> {
        if (sala == null) return emptyList()
        return _itemsPorMesa.value?.get("$sala-$mesa") ?: emptyList()
    }
    fun seleccionarSala(sala: String?) {
        _salaSeleccionada.value = sala
        val clave = claveSalaMesa() ?: return
        val mapaIds = _idPedidoPorMesa.value ?: mutableMapOf()
        if (!mapaIds.containsKey(clave)) {
            val nuevoId = generarIdUnicoPedido()
            mapaIds[clave] = nuevoId
            _idPedidoPorMesa.value = mapaIds
        }
    }

    private fun generarIdUnicoPedido(): String {
        val uuid = UUID.randomUUID().toString()
        val digitsOnly = uuid.filter { it.isDigit() }
        return digitsOnly.take(9) // Adjust length if needed
    }

    private fun claveSalaMesa(): String? {
        val sala = _salaSeleccionada.value ?: return null
        val mesa = _mesaSeleccionada.value ?: return null
        return "$sala-$mesa"
    }

    fun seleccionarMesa(mesa: String) {
        _mesaSeleccionada.value = mesa
        val clave = claveSalaMesa() ?: return

        // Asegura que hay ID asociado
        val mapaIds = _idPedidoPorMesa.value ?: mutableMapOf()
        if (!mapaIds.containsKey(clave)) {
            mapaIds[clave] = generarIdUnicoPedido()
            _idPedidoPorMesa.value = mapaIds
        }

        // Asegura que hay lista de productos
        val mapaProd = _itemsPorMesa.value ?: mutableMapOf()
        if (!mapaProd.containsKey(clave)) {
            mapaProd[clave] = mutableListOf()
            _itemsPorMesa.value = mapaProd
        }
    }

    fun obtenerIdPedidoMesaSeleccionada(): String? {
        val clave = claveSalaMesa() ?: return null
        val mapa = _idPedidoPorMesa.value ?: mutableMapOf()

        // ⚠️ Generar el reg si no existe todavía
        if (!mapa.containsKey(clave)) {
            val nuevoId = generarIdUnicoPedido()
            mapa[clave] = nuevoId
            _idPedidoPorMesa.value = mapa
        }
        return _idPedidoPorMesa.value?.get(clave)
    }

    fun liberarPantallaActual() {
        _mesaSeleccionada.value = "sin mesa"
        _salaSeleccionada.value = null
    }

    fun cargarPedidosPendientesDesdeBD(dbId: String, colorNet: String) {
        Log.i("PedidoVM", "▶ cargarPedidosPendientesDesdeBD(dbId=$dbId, colorNet=$colorNet) llamada")
        RetrofitClient.apiService.getPedidosPendientes(dbId, colorNet)
            .enqueue(object : Callback<List<Pedido>> {
                override fun onResponse(call: Call<List<Pedido>>, response: Response<List<Pedido>>) {
                    if (!response.isSuccessful) return
                    val pedidos = response.body() ?: return

                    // LinkedHashMap para mantener orden de inserción
                    val mapa = linkedMapOf<String, MutableList<ItemPedido>>()
                    val ids  = mutableMapOf<String, String>()

                    pedidos.forEach { linea ->
                        Log.d("PedidoVM", "LINEA → Plu=${linea.Plu}  PluAdbc=${linea.PluAdbc}  Producto='${linea.Producto}'")
                        val clave = "${linea.NombreFormaPago}-${linea.PagoPendiente}"
                        ids[clave] = linea.reg
                        val lista = mapa.getOrPut(clave) { mutableListOf() }

                        if (linea.PluAdbc == 90909090) {
                            // Es propiedad: la agrego a la última línea base
                            if (lista.isNotEmpty()) {
                                lista.last().propiedades.add(linea.Producto)
                            }
                        } else {
                            // Producto base → creo nuevo ItemPedido
                            val precio  = linea.Tarifa.replace(",",".").toDoubleOrNull() ?: 0.0
                            lista.add(
                                ItemPedido(
                                    nombre      = linea.Producto,
                                    nombreBase  = linea.Producto,
                                    precio      = precio,
                                    cantidad    = 1,
                                    plu         = linea.Plu,
                                    familia     = linea.Familia,
                                    consumoSolo = linea.Consumo,
                                    impresora   = linea.Impreso,
                                    ivaVenta    = linea.IvaVenta,
                                    propiedades = mutableListOf(),
                                    pluadbc = linea.PluAdbc
                                )
                            )
                        }
                    }

                    _itemsPorMesa.value    = mapa
                    _idPedidoPorMesa.value = ids
                }

                override fun onFailure(call: Call<List<Pedido>>, t: Throwable) {
                    Log.e("PedidoVM", "Error cargando pendientes", t)
                }
            })
    }
}
