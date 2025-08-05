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
        val mapa  = _itemsPorMesa.value ?: mutableMapOf()
        val lista = mapa.getOrPut(clave) { mutableListOf() }
        /* ─── buscamos un hueco válido ─── */
        val idxValido = lista
            .withIndex()
            .indexOfFirst { (idx, it) ->
                it.plu == item.plu &&
                        !it.esCombinado() &&
                        !lista.itemTieneCombinados(idx)
            }

        if (idxValido >= 0) {
            lista[idxValido].cantidad++
        } else {
            lista.add(item)
        }

        mapa[clave] = lista
        _itemsPorMesa.value = mapa
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
        RetrofitClient.apiService.getPedidosPendientes(dbId, colorNet)
            .enqueue(object : Callback<List<Pedido>> {
                override fun onResponse(call: Call<List<Pedido>>, response: Response<List<Pedido>>) {
                    if (!response.isSuccessful) return
                    val pedidos = response.body() ?: return

                    // --- Mantenemos orden de inserción
                    val mapa = linkedMapOf<String, MutableList<ItemPedido>>()
                    val ids  = mutableMapOf<String, String>()

                    pedidos.forEach { linea ->
                        val clave = "${linea.NombreFormaPago}-${linea.PagoPendiente}"
                        ids[clave] = linea.reg
                        val lista = mapa.getOrPut(clave) { mutableListOf() }

                        if (linea.PluAdbc == 90909090) {
                            // Las tratamos como un ItemPedido independiente
                            val precio = linea.Pts.replace(",", ".").toDoubleOrNull() ?: 0.0
                            lista.add(
                                ItemPedido(
                                    nombreBase   = linea.Producto,
                                    nombre       = linea.Producto,
                                    precio       = precio,
                                    cantidad     = linea.Cantidad.toIntOrNull() ?: 1,
                                    plu          = linea.Plu,
                                    familia      = linea.Familia,
                                    consumoSolo  = linea.Consumo,
                                    impresora    = linea.Impreso,
                                    ivaVenta     = linea.IvaVenta,
                                    pluadbc      = 90909090,
                                    propiedades  = mutableListOf()
                                )
                            )
                            return@forEach
                        }

                        val precioBase = linea.Pts.replace(",", ".").toDoubleOrNull() ?: 0.0
                        lista.add(
                            ItemPedido(
                                nombreBase   = linea.Producto,
                                nombre       = linea.Producto,
                                precio       = precioBase,
                                cantidad     = linea.Cantidad.toIntOrNull() ?: 1,
                                plu          = linea.Plu,
                                familia      = linea.Familia,
                                consumoSolo  = linea.Consumo,
                                impresora    = linea.Impreso,
                                ivaVenta     = linea.IvaVenta,
                                pluadbc      = 0,
                                propiedades  = mutableListOf()
                            )
                        )
                    }

                    _itemsPorMesa.value    = mapa
                    _idPedidoPorMesa.value = ids
                }

                override fun onFailure(call: Call<List<Pedido>>, t: Throwable) {
                    Log.e("PedidoVM", "Error cargando pendientes", t)
                }
            })
    }


    /** Inserta `nuevo` justo después de `base` dentro de la lista de items de esa mesa. */
    fun insertarItemDespues(base: ItemPedido, nuevo: ItemPedido) {
        val clave = claveSalaMesa() ?: return
        val lista = _itemsPorMesa.value?.get(clave)?.toMutableList() ?: return

        // encuentra índice de la base y mete nuevo justo tras él
        val idx = lista.indexOfFirst { it === base }
        val pos = if (idx != -1) idx + 1 else lista.size
        lista.add(pos, nuevo)

        _itemsPorMesa.value = _itemsPorMesa.value!!.toMutableMap().apply {
            put(clave, lista)
        }
    }
}

private fun ItemPedido.esCombinado(): Boolean = this.pluadbc == 90909090

/** Devuelve true si inmediatamente después hay al menos un combinado */
fun MutableList<ItemPedido>.itemTieneCombinados(idx: Int): Boolean {
    if (idx !in indices) return false
    var j = idx + 1
    while (j < size && this[j].esCombinado()) return true
    return false
}