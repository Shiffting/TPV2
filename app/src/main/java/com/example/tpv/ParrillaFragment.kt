package com.example.tpv

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.text.LineBreaker
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.TextUtils
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import com.example.tpv.data.api.RetrofitClient
import com.example.tpv.data.model.Pedido
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.widget.Toast
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.GridLayout
import android.widget.NumberPicker
import com.example.tpv.data.model.FamiliaProducto
import com.example.tpv.viewModels.PedidoViewModel
import com.example.tpv.viewModels.ProductosViewModel
import com.google.android.material.button.MaterialButton
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.properties.Delegates
import androidx.core.graphics.toColorInt
import androidx.core.widget.TextViewCompat
import com.example.tpv.data.model.ItemPedido
import com.example.tpv.data.model.Producto
import com.example.tpv.data.model.Propiedades
import com.example.tpv.data.model.Sala
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import kotlin.apply
import androidx.core.content.edit

enum class ModoEnvio { PENDIENTE, IMPRIMIR, COBRAR }

class ParrillaFragment : Fragment() {
    private val pedidoViewModel: PedidoViewModel by activityViewModels()
    private val productosViewModel: ProductosViewModel by activityViewModels()
    private lateinit var layoutCategorias: GridLayout
    private lateinit var layoutProductos: GridLayout
    private lateinit var layoutTicketItems: LinearLayout
    private var mesaActual: String = "sin mesa"
    private var salaActual: String = "sin sala"
    private var idLocal by Delegates.notNull<Int>()
    private lateinit var nombreLocal: String
    private lateinit var terminal: String
    private lateinit var camarero: String
    private val handler = Handler(Looper.getMainLooper())
    private var refrescoActivo = false
    private var productosCargados = false
    private var pedidosCargados = false
    private lateinit var btnPrimeros: Button
    private lateinit var btnPropiedades: Button
    private lateinit var btnCombinado: Button
    private lateinit var sharedPref: SharedPreferences
    private var ultimoItemSeleccionado: ItemPedido? = null
    private val productoOriginalMap = mutableMapOf<Int, Producto>()
    private var modoCombinados = false// justo debajo de `private var modoCombinados = false`
    private var modoPrimerosSegundos = false
    private var modoCombinado = false
    private var productoBaseParaCombinados: ItemPedido? = null
    private var tarifaPredetSala: String = "Tarifa1"
    private lateinit var btnCobrar: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_parrilla, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPref = requireContext().getSharedPreferences("TPV_PREFS", Context.MODE_PRIVATE)

        idLocal = sharedPref.getInt("local_id", -1)
        nombreLocal = sharedPref.getString("local_nombre", null).toString()
        terminal = sharedPref.getString("terminal", null).toString()
        camarero = sharedPref.getString("camarero", null).toString()

        val dbId = sharedPref.getString("dbId", "cloud")!!
        val colorNet = sharedPref.getString("local_nombre", "")!!
        pedidoViewModel.cargarPedidosPendientesDesdeBD(dbId, colorNet)


        layoutCategorias = view.findViewById(R.id.layoutCategorias)
        layoutProductos = view.findViewById(R.id.layoutProductos)
        layoutTicketItems = view.findViewById(R.id.layoutTicketItems)

        // Cargar datos del ViewModel (que a su vez los carga de la base de datos)
        productosViewModel.cargarFamilias(
            sharedPref.getString("dbId", "cloud").toString(),
            nombreLocal
        )
        productosViewModel.cargarProductos(
            sharedPref.getString("dbId", "cloud").toString(),
            nombreLocal
        )

        // Observar familias y mostrarlas como botones categorías
        productosViewModel.familias.observe(viewLifecycleOwner) { familias ->
            val familiasOrdenadas = familias
                .filter { it.VISIBLETPV == 1 }
                .sortedBy { it.NOrden.toIntOrNull() ?: 0 }
            mostrarCategorias(familiasOrdenadas)
            familiasOrdenadas.firstOrNull()?.let { mostrarProductosDeFamilia(it.Nombre) }
        }

        productosViewModel.productos.observe(viewLifecycleOwner) { listaProductos ->
            // 1) Reemplaza o vacía el mapa
            productoOriginalMap.clear()
            // 2) Asocia cada plu a su Producto
            productoOriginalMap.putAll(listaProductos.associateBy { it.Plu })
            // 3) Ahora puedes mostrar las familias/productos con ese mapa disponible
            productosCargados = true
            actualizarUIProductos()
        }

        // Actualizamos la UI del ticket cuando cambian productos en la mesa
        pedidoViewModel.itemsPorMesa.observe(viewLifecycleOwner) {
            pedidosCargados = true
            actualizarUIProductos()
        }

        // Actualizar mesa seleccionada
        pedidoViewModel.mesaSeleccionada.observe(viewLifecycleOwner) { mesa ->
            mesaActual = mesa ?: "sin mesa"
            salaActual = pedidoViewModel.salaSeleccionada.value ?: "sin sala"

            val textMesaView = view.findViewById<TextView>(R.id.textMesa)
            textMesaView?.text = "$salaActual: $mesaActual"

            val com = pedidoViewModel.getComensales(salaActual, mesaActual)
            textMesaView?.text = "$salaActual: $mesaActual ($com)"

            // Long-press para editar comensales de esta mesa
            textMesaView?.setOnLongClickListener {
                mostrarDialogoComensales()
                true
            }

            cargarTarifaPredetParaSala(salaActual)

            actualizarUIProductos()
        }

        val btnPendiente = view.findViewById<Button>(R.id.btnPendiente)
        applyAutoSize(btnPendiente, minSp = 10, maxSp = 16, stepSp = 1, maxLines = 2)
        btnPendiente.setOnClickListener {
            enviarPedidoPendiente(ModoEnvio.PENDIENTE)
        }

        val btnImprimir = view.findViewById<Button>(R.id.btnImprimir)
        applyAutoSize(btnImprimir, minSp = 10, maxSp = 16, stepSp = 1, maxLines = 2)
        btnImprimir.setOnClickListener {
            enviarPedidoPendiente(ModoEnvio.IMPRIMIR)
        }

        btnCobrar = view.findViewById(R.id.btnCobrar)
        val activo = sharedPref.getString("cobrar_activado", "0")
        Log.d("cobrar", activo.toString())
        btnCobrar.visibility = if (activo.toString() == "1") View.VISIBLE else View.GONE
        applyAutoSize(btnCobrar, minSp = 10, maxSp = 16, stepSp = 1, maxLines = 2)
        btnCobrar.setOnClickListener {
            enviarPedidoPendiente(ModoEnvio.COBRAR)
        }

        btnPropiedades = view.findViewById<Button>(R.id.btnPropiedades)
        applyAutoSize(btnPropiedades, minSp = 10, maxSp = 16, stepSp = 1, maxLines = 2)
        btnPropiedades.setOnClickListener {
            val item = ultimoItemSeleccionado ?: return@setOnClickListener
            val productoOriginal = productoOriginalMap[item.plu] ?: return@setOnClickListener
            val colorNet = sharedPref.getString("local_nombre", "")!!

            // 2) Hacer la llamada
            RetrofitClient.apiService.getPropiedades(
                sharedPref.getString("dbId", "cloud").toString(),
                productoOriginal.Familia,
                productoOriginal.Producto,
                colorNet
            )
                .enqueue(object : Callback<List<Propiedades>> {
                    override fun onResponse(
                        call: Call<List<Propiedades>>,
                        response: Response<List<Propiedades>>
                    ) {
                        val props = response.body().orEmpty()
                        Log.d(
                            "Parrilla",
                            "Propiedades recibidas: ${props.size} ${productoOriginal.Familia} ${productoOriginal.Producto} ${colorNet}"
                        )
                        showPropiedadesDialog(props, productoOriginal, ultimoItemSeleccionado!!)
                    }

                    override fun onFailure(
                        call: Call<List<Propiedades>>,
                        t: Throwable
                    ) {
                        Toast.makeText(context, "Error al cargar propiedades", Toast.LENGTH_SHORT)
                            .show()
                    }
                })
        }
        btnCombinado = view.findViewById<Button>(R.id.btnCombinado)
        applyAutoSize(btnCombinado, minSp = 10, maxSp = 16, stepSp = 1, maxLines = 2)
        btnCombinado.text = "COMBINADOS"
        btnCombinado.setOnClickListener {
            if (!modoCombinado) {
                // Entramos en modo Combinados
                productoBaseParaCombinados = ultimoItemSeleccionado
                modoCombinado = true
                btnCombinado.text = "FIN"
                btnCombinado.alpha = 1f
            } else {
                // Salimos del modo
                modoCombinado = false
                productoBaseParaCombinados = null
                btnCombinado.text = "COMBINADOS"
            }
        }

        btnPrimeros = view.findViewById<Button>(R.id.btnPrimeros)
        applyAutoSize(btnPrimeros, minSp = 10, maxSp = 16, stepSp = 1, maxLines = 2)
        btnPrimeros.text = "PRIMEROS"
        btnPrimeros.setOnClickListener {
            // alternamos el modo
            modoPrimerosSegundos = !modoPrimerosSegundos

            // determinamos el header y el texto del botón
            val headerName = if (modoPrimerosSegundos) "--PRIMEROS--" else "--SEGUNDOS--"
            btnPrimeros.text = if (modoPrimerosSegundos) "SEGUNDOS" else "PRIMEROS"

            // creamos un ItemPedido especial para el header
            val headerItem = ItemPedido(
                nombreBase = headerName,
                nombre = headerName,
                precio = 0.0,
                plu = 1,
                familia = "",            // sin familia
                consumoSolo = "",            // según tu modelo
                impresora = "6",             // tal y como pides
                ivaVenta = "0",
                pluadbc = 90909090,      // identifica “propiedad / header”
                propiedades = mutableListOf()
            )

            // lo añadimos al ViewModel y refrescamos
            pedidoViewModel.añadirItem(headerItem)
            actualizarUIProductos()
        }
        iniciarRefrescoMesas()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        refrescoActivo = false
        handler.removeCallbacksAndMessages(null)
    }

    @SuppressLint("SetTextI18n")
    private fun mostrarDialogoComensales() {
        if (salaActual == "sin sala" || mesaActual == "sin mesa") {
            Toast.makeText(requireContext(), "Selecciona una sala y mesa primero", Toast.LENGTH_SHORT).show()
            return
        }

        val actual = pedidoViewModel.getComensales(salaActual, mesaActual)
        val picker = NumberPicker(requireContext()).apply {
            minValue = 1
            maxValue = 20
            value = actual
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Comensales en $mesaActual")
            .setView(picker)
            .setPositiveButton("Guardar") { _, _ ->
                pedidoViewModel.setComensales(salaActual, mesaActual, picker.value)
                // Actualiza el header
                view?.findViewById<TextView>(R.id.textMesa)?.text = "$salaActual: $mesaActual (${picker.value})"
                Toast.makeText(requireContext(), "Comensales actualizados", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // —— Helpers de normalización para la cache por sala ——
    private fun salaKey(s: String) = s.trim().lowercase()
    private fun tarifaCacheKey(sala: String) = "tarifa_predet_sala_${salaKey(sala)}"

    private fun cargarTarifaPredetParaSala(nombreSala: String) {
        val db = sharedPref.getString("dbId", "cloud")!!
        val colorNet = sharedPref.getString("local_nombre", "") ?: ""
        val cacheKey = tarifaCacheKey(nombreSala)

        // 1) Cargar INMEDIATO desde cache por sala si existe (evita precios “de la sala anterior”)
        val cached = sharedPref.getString(cacheKey, null)
        tarifaPredetSala = normalizaTarifaKey(cached ?: sharedPref.getString("tarifa_predet_sala_global", null) ?: "Tarifa1")
        // ^ también intentamos un viejo global como fallback, y finalmente Tarifa1
        // Log de diagnóstico
        Log.d("TarifaSala", "cache hit? ${cached != null} | sala=${nombreSala} -> ${tarifaPredetSala}")

        // 2) Actualizar desde red (no bloqueante). Al volver, refrescamos el valor y guardamos cache.
        RetrofitClient.apiService.getSalas(db, colorNet)
            .enqueue(object : Callback<List<Sala>> {
                override fun onResponse(call: Call<List<Sala>>, response: Response<List<Sala>>) {
                    if (!response.isSuccessful) return
                    val lista = response.body().orEmpty()
                    val target = salaKey(nombreSala)
                    val sala = lista.firstOrNull { salaKey(it.denominacion) == target }

                    val claveNueva = normalizaTarifaKey(
                        sala?.tarifaPredet
                            ?: sharedPref.getString(cacheKey, null)
                            ?: "Tarifa1"
                    )

                    tarifaPredetSala = claveNueva
                    // Guardamos por sala y, opcional, una global del “último éxito”
                    sharedPref.edit()
                        .putString(cacheKey, claveNueva)
                        .putString("tarifa_predet_sala_global", claveNueva)
                        .apply()

                    Log.d("TarifaSala", "net ok | sala=${nombreSala} -> ${tarifaPredetSala}")
                }

                override fun onFailure(call: Call<List<Sala>>, t: Throwable) {
                    // No cambiamos tarifaPredetSala; ya está con cache/fallback
                    Log.w("TarifaSala", "net fail, using cached=${tarifaPredetSala}")
                }
            })
    }

    private fun iniciarRefrescoMesas() {
        refrescoActivo = true
        handler.post(object : Runnable {
            override fun run() {
                if (refrescoActivo) {
                    val dbId = sharedPref.getString("dbId", "cloud") ?: "cloud"
                    val colorNet = sharedPref.getString("local_nombre", "") ?: ""

                    pedidoViewModel.cargarPedidosPendientesDesdeBD(dbId, colorNet)

                    // Refrescar cada 120 segundos
                    handler.postDelayed(this, 120_000)
                }
            }
        })
    }

    private fun limpiarVistaDeTicket() {
        // Aquí limpias la lista que muestra los productos del pedido actual
        layoutTicketItems.removeAllViews()
    }

    private fun mostrarCategorias(familias: List<FamiliaProducto>) {
        layoutCategorias.removeAllViews()

        Log.d("ParrillaFragment", "Familias recibidos: $familias")

        familias.filter { it.VISIBLETPV == 1 }.sortedBy { it.NOrden }.forEach { familia ->
            val boton = MaterialButton(requireContext()).apply {
                text = familia.Nombre
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    setMargins(8, 8, 8, 8)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
                isAllCaps = false
                setPadding(12, 24, 12, 24)
                gravity = Gravity.CENTER
                textSize = 14f
                setOnClickListener {
                    mostrarProductosDeFamilia(familia.Nombre)
                }
            }
            layoutCategorias.addView(boton)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun mostrarProductosDeFamilia(nombreFamilia: String) {
        Log.d("producto", productosViewModel.productos.value.toString())
        val productos = productosViewModel.productos.value
            ?.filter { it.Familia == nombreFamilia && it.VISIBLETPV == 1 && it.ColorNet == nombreLocal }
            ?.sortedBy { it.Orden.toIntOrNull() ?: 0 }
            ?: emptyList()

        layoutProductos.removeAllViews()

        for (producto in productos) {
            val btn = MaterialButton(requireContext()).apply {
                text = producto.Producto
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    setMargins(8, 8, 8, 8)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this,
                    /* min */ 10, /* max */ 16, /* step */ 1,
                    TypedValue.COMPLEX_UNIT_SP
                )
                isSingleLine = false
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END

                // (Opcional) Mejor corte de palabras en varias líneas (API 23+)
                hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
                breakStrategy = LineBreaker.BREAK_STRATEGY_BALANCED
                setPadding(12, 24, 12, 24)
                gravity = Gravity.CENTER
                textSize = 14f
                setBackgroundColor("#2196F3".toColorInt()) // Naranja
                setOnClickListener {
                    // 1) Determinar tarifa y sufijo según modoCombinados
                    val sufijo = if (modoCombinados) "_Cb" else ""
                    val nombreCb = producto.Producto + sufijo

                    val precioBase = if (modoCombinados)
                        precioPorTarifa(producto, "Tarifa15")
                    else
                        precioPorTarifa(producto, tarifaPredetSala)

                    val tarifaNombre = if (modoCombinados) "Tarifa15" else tarifaPredetSala

                    if (modoCombinado && productoBaseParaCombinados != null) {
                        // —–––– modo Combinados: generar un ÍTEM “propiedad” pero con tarifa15 —––––
                        val base = productoBaseParaCombinados!!
                        val nombreCb = "${producto.Producto}_Cb"
                        val price15 = producto.Tarifa15.replace(",", ".").toDoubleOrNull() ?: 0.0

                        val combinadoItem = ItemPedido(
                            nombreBase = producto.Producto,
                            nombre = nombreCb,
                            precio = price15,
                            cantidad = 1,
                            plu = producto.Plu,
                            familia = producto.Familia,
                            consumoSolo = producto.ConsumoSolo,
                            impresora = producto.Impresora,
                            ivaVenta = producto.IvaVenta,
                            pluadbc = 90909090,
                            propiedades = mutableListOf(),
                            tarifaUsada = "Tarifa15",
                            yaIntroducido = false
                        )

                        // Insertamos justo después del base:
                        pedidoViewModel.insertarItemDespues(base, combinadoItem)
                    } else {
                        val item = ItemPedido(
                            nombreBase = producto.Producto,
                            nombre = nombreCb,
                            precio = precioBase,
                            cantidad = 1,
                            plu = producto.Plu,
                            familia = producto.Familia,
                            consumoSolo = producto.ConsumoSolo,
                            impresora = producto.Impresora,
                            ivaVenta = producto.IvaVenta,
                            pluadbc = producto.PluAdbc,
                            propiedades = mutableListOf(),
                            tarifaUsada = tarifaNombre,
                            yaIntroducido = false
                        )
                        pedidoViewModel.añadirItem(item)
                        ultimoItemSeleccionado = item
                        btnCombinado.alpha = 0.8f
                        btnCombinado.text = "COMBINADOS"
                    }
                    actualizarUIProductos()
                }
            }
            layoutProductos.addView(btn)
        }
    }

    private fun showPropiedadesDialog(
        apiProps: List<Propiedades>,
        producto: Producto,
        item: ItemPedido
    ) {
        // 1) Montamos la lista de todas las props posibles
        val allLabels = mutableListOf<String>().apply {
            apiProps.mapTo(this) { it.PROPIEDAD }
            if (producto.TextoBotonTapa != "-") add(producto.TextoBotonTapa)
            if (producto.TextoBotonMediaRacion != "-") add(producto.TextoBotonMediaRacion)
        }

        // 2) Localizamos índices de Tapa/Media (si existen)
        val tapaLabel = producto.TextoBotonTapa.takeIf { it != "-" }
        val mediaLabel = producto.TextoBotonMediaRacion.takeIf { it != "-" }
        val tapaIdx = tapaLabel?.let { allLabels.indexOf(it) } ?: -1
        val mediaIdx = mediaLabel?.let { allLabels.indexOf(it) } ?: -1

        // 3) Si por cualquier motivo estaban ambas marcadas, dejamos solo Tapa
        if (tapaIdx >= 0 && mediaIdx >= 0 &&
            item.propiedades.contains(tapaLabel) &&
            item.propiedades.contains(mediaLabel)
        ) {
            item.propiedades.remove(mediaLabel)
        }

        // 4) Array de checados sincronizado con item.propiedades
        val checked = BooleanArray(allLabels.size) { idx ->
            item.propiedades.contains(allLabels[idx])
        }

        // 5) Creamos el diálogo para poder desmarcar programáticamente
        var dlg: androidx.appcompat.app.AlertDialog? = null
        val builder =
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Propiedades para ${item.nombreBase}")
                .setMultiChoiceItems(allLabels.toTypedArray(), checked) { _, which, isChecked ->
                    val label = allLabels[which]

                    if (isChecked) {
                        if (!item.propiedades.contains(label)) item.propiedades.add(label)

                        // ——— Exclusión mutua: Tapa vs Media ———
                        if (which == tapaIdx && mediaIdx >= 0) {
                            mediaLabel?.let { item.propiedades.remove(it) }
                            if (mediaIdx in checked.indices) {
                                checked[mediaIdx] = false
                                dlg?.listView?.setItemChecked(mediaIdx, false)
                            }
                        } else if (which == mediaIdx && tapaIdx >= 0) {
                            tapaLabel?.let { item.propiedades.remove(it) }
                            if (tapaIdx in checked.indices) {
                                checked[tapaIdx] = false
                                dlg?.listView?.setItemChecked(tapaIdx, false)
                            }
                        }
                    } else {
                        item.propiedades.remove(label)
                    }
                }
                .setPositiveButton("OK") { _, _ ->
                    // Nombre base + lista de propiedades
                    val tarifaBase = precioPorTarifa(producto, tarifaPredetSala)
                    val (nuevoPrecio, nuevaTarifaNombre) = when {
                        "Chupito" in item.propiedades -> precioPorTarifa(
                            producto,
                            "Tarifa11"
                        ) to "Tarifa11"

                        "Combinado" in item.propiedades -> precioPorTarifa(
                            producto,
                            "Tarifa15"
                        ) to "Tarifa15"

                        producto.TextoBotonTapa in item.propiedades -> precioPorTarifa(
                            producto,
                            "Tarifa13"
                        ) to "Tarifa13"

                        producto.TextoBotonMediaRacion in item.propiedades -> precioPorTarifa(
                            producto,
                            "Tarifa14"
                        ) to "Tarifa14"

                        else -> tarifaBase to tarifaPredetSala
                    }

                    val extra =
                        item.propiedades.sumOf { _ -> 0.0 } // placeholder por si añades recargos
                    item.precio = nuevoPrecio + extra
                    item.tarifaUsada = nuevaTarifaNombre

                    // Refrescamos UI
                    actualizarUIProductos()
                }
                .setNegativeButton("Cancelar", null)

        dlg = builder.create()
        dlg.show()
    }


    @SuppressLint("SetTextI18n")
    private fun actualizarUIProductos() {
        layoutTicketItems.removeAllViews()

        val items = pedidoViewModel.obtenerItems(mesaActual, salaActual)
        var total = 0.0

        items.forEachIndexed { idx, it ->
            if (!it.esCombinado()) {
                layoutTicketItems.addView(filaPrincipal(it))
            } else {
                layoutTicketItems.addView(filaCombinado(it))
            }

            it.propiedades.forEach { p -> layoutTicketItems.addView(filaProp(p)) }

            total += it.precio * it.cantidad
        }

        view?.findViewById<TextView>(R.id.textTotal)
            ?.text = "Total: %.2f €".format(total)
    }

    @SuppressLint("SetTextI18n")
    private fun enviarPedidoPendiente(modo: ModoEnvio) {
        val items = pedidoViewModel.obtenerItems(mesaActual, salaActual)
        val now = LocalDateTime.now()
        val fecha = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val hora = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val nombreCam = sharedPref.getString("empleado_nombre", "CAMARERA_DESCONOCIDA")!!
        val dbId = sharedPref.getString("dbId", "cloud")!!
        val idPedido = pedidoViewModel.obtenerIdPedidoMesaSeleccionada() ?: return

        val esImprimir = (modo == ModoEnvio.IMPRIMIR)
        val esCobrar = (modo == ModoEnvio.COBRAR)

        // Helper que construye TODA la mesa (producto + props) para un destino
        fun buildFullLines(nombreFPagoOriginal: String? = null): MutableList<Pedido> {
            val full = mutableListOf<Pedido>()
            val comensales = pedidoViewModel.getComensales(salaActual, mesaActual).toString()
            for (item in items) {
                if (item.propiedades.isEmpty()) {
                    full += Pedido(
                        reg = idPedido,
                        Hora = hora,
                        NombreCam = nombreCam,
                        NombreFormaPago = salaActual,
                        Fecha = fecha,
                        FechaReg = fecha,
                        Barra = 1,
                        Terminal = 1,
                        Plu = item.plu,
                        Producto = item.nombreBase,
                        Cantidad = item.cantidad.toString(),
                        Pts = item.precio.toString(),
                        ImpresoCli = 0,
                        Tarifa = item.tarifaUsada,
                        CBarras = terminal,
                        PagoPendiente = mesaActual,
                        Comensales = comensales,
                        Consumo = item.consumoSolo,
                        IDCLIENTE = "0A_VENTA",
                        Impreso = item.impresora,
                        NombTerminal = nombreLocal,
                        IvaVenta = item.ivaVenta,
                        Iva = item.ivaVenta,
                        TotalReg = (item.precio * item.cantidad).toString(),
                        incluirConfirmacion = esImprimir,
                        incluirCobro = esCobrar,
                        Familia = item.familia,
                        PluAdbc = item.pluadbc,
                        NombreFPagoOriginal = nombreFPagoOriginal
                    )
                } else {
                    repeat(item.cantidad) {
                        full += Pedido(
                            reg = idPedido, Hora = hora, NombreCam = nombreCam,
                            NombreFormaPago = salaActual, Fecha = fecha, FechaReg = fecha,
                            Barra = 1, Terminal = 1, Plu = item.plu, Producto = item.nombreBase,
                            Cantidad = "1", Pts = item.precio.toString(), ImpresoCli = 0,
                            Tarifa = item.tarifaUsada, CBarras = terminal,
                            PagoPendiente = mesaActual, Comensales = comensales,
                            Consumo = item.consumoSolo, IDCLIENTE = "0A_VENTA",
                            Impreso = item.impresora, NombTerminal = nombreLocal,
                            IvaVenta = item.ivaVenta, Iva = item.ivaVenta,
                            TotalReg = item.precio.toString(),
                            incluirConfirmacion = esImprimir,
                            incluirCobro = esCobrar,
                            Familia = item.familia, PluAdbc = item.pluadbc,
                            NombreFPagoOriginal = nombreFPagoOriginal
                        )
                        item.propiedades.forEach { prop ->
                            full += Pedido(
                                reg = idPedido, Hora = hora, NombreCam = nombreCam,
                                NombreFormaPago = salaActual, Fecha = fecha, FechaReg = fecha,
                                Barra = 1, Terminal = 1, Plu = item.plu, Producto = prop,
                                Cantidad = "1", Pts = "0", ImpresoCli = 0,
                                Tarifa = "0", CBarras = terminal,
                                PagoPendiente = mesaActual, Comensales = comensales,
                                Consumo = item.consumoSolo, IDCLIENTE = "0A_VENTA",
                                Impreso = item.impresora, NombTerminal = nombreLocal,
                                IvaVenta = item.ivaVenta, Iva = item.ivaVenta,
                                TotalReg = "0",
                                incluirConfirmacion = esImprimir,
                                incluirCobro = esCobrar,
                                Familia = item.familia, PluAdbc = 90909090,
                                NombreFPagoOriginal = nombreFPagoOriginal
                            )
                        }
                    }
                }
            }
            return full
        }

        // 1) IMPRIMIR y COBRAR necesitan “mesa completa”.
        //    PENDIENTE no (solo delta).
        if (esImprimir) {
            val full = buildFullLines(null)
            enviarLineasEnSerie(dbId, idPedido, full)
            val claveMesa = "${salaActual}-${mesaActual}"
            sharedPref.edit { putBoolean("mesa_impresa_$claveMesa", true) }
        } else if (esCobrar) {
            val claveMesa = "${salaActual}-${mesaActual}"
            sharedPref.edit { remove("mesa_impresa_$claveMesa") }
            // Pedimos al usuario la forma de pago válida (Esp1=1) y luego enviamos a PendTotal + borrado Pend
            val colorNet = sharedPref.getString("local_nombre", "") ?: ""
            RetrofitClient.apiService.getFormasPagoCobro(dbId, colorNet)
                .enqueue(object : Callback<List<String>> {
                    override fun onResponse(
                        call: Call<List<String>>,
                        response: Response<List<String>>
                    ) {
                        val opciones = response.body().orEmpty()
                        if (opciones.isEmpty()) {
                            Toast.makeText(
                                requireContext(),
                                "No hay formas de cobro disponibles",
                                Toast.LENGTH_SHORT
                            ).show()
                            return
                        }
                        // Dialogo de selección
                        var selected = 0
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Selecciona forma de cobro")
                            .setSingleChoiceItems(opciones.toTypedArray(), 0) { _, which ->
                                selected = which
                            }
                            .setPositiveButton("Aceptar") { _, _ ->
                                val fp = opciones[selected]
                                val full = buildFullLines(fp)
                                enviarLineasEnSerie(dbId, idPedido, full, onFinish = {
                                    // Al terminar de insertar en PendTotal, borramos Pend de esta mesa(reg)
                                    RetrofitClient.apiService
                                        .borrarPendientesPorReg(dbId, idPedido)
                                        .enqueue(object : Callback<Void> {
                                            override fun onResponse(
                                                call: Call<Void>,
                                                r: Response<Void>
                                            ) {
                                            }

                                            override fun onFailure(
                                                call: Call<Void>,
                                                t: Throwable
                                            ) {
                                            }
                                        })
                                })
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }

                    override fun onFailure(call: Call<List<String>>, t: Throwable) {
                        Toast.makeText(
                            requireContext(),
                            "Error cargando formas de cobro",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
        }

        // 2) DELTA → Pend (para PENDIENTE e IMPRIMIR; NO para COBRAR)
        if (!esCobrar) {
            val lineasPend = mutableListOf<Pedido>()
            val comensales = pedidoViewModel.getComensales(salaActual, mesaActual).toString()
            for (item in items) {
                val nuevas = item.cantidad - item.introducidas
                if (nuevas > 0) {
                    lineasPend += Pedido(
                        reg = idPedido, Hora = hora, NombreCam = nombreCam,
                        NombreFormaPago = salaActual, Fecha = fecha, FechaReg = fecha,
                        Barra = 1, Terminal = 1, Plu = item.plu, Producto = item.nombreBase,
                        Cantidad = nuevas.toString(), Pts = item.precio.toString(), ImpresoCli = 0,
                        Tarifa = item.tarifaUsada, CBarras = terminal,
                        PagoPendiente = mesaActual, Comensales = comensales,
                        Consumo = item.consumoSolo, IDCLIENTE = "0A_VENTA",
                        Impreso = item.impresora, NombTerminal = nombreLocal,
                        IvaVenta = item.ivaVenta, Iva = item.ivaVenta,
                        TotalReg = (item.precio * nuevas).toString(),
                        incluirConfirmacion = false, incluirCobro = false,
                        Familia = item.familia, PluAdbc = item.pluadbc
                    )
                    val totalProps = item.propiedades.size
                    val nuevasProps = totalProps - item.propsIntroducidas
                    if (nuevasProps > 0) {
                        val propsAEnviar = item.propiedades.takeLast(nuevasProps)
                        propsAEnviar.forEach { prop ->
                            lineasPend += Pedido(
                                reg = idPedido, Hora = hora, NombreCam = nombreCam,
                                NombreFormaPago = salaActual, Fecha = fecha, FechaReg = fecha,
                                Barra = 1, Terminal = 1, Plu = item.plu, Producto = prop,
                                Cantidad = "1", Pts = "0", ImpresoCli = 0,
                                Tarifa = "0", CBarras = terminal,
                                PagoPendiente = mesaActual, Comensales = comensales,
                                Consumo = item.consumoSolo, IDCLIENTE = "0A_VENTA",
                                Impreso = item.impresora, NombTerminal = nombreLocal,
                                IvaVenta = item.ivaVenta, Iva = item.ivaVenta,
                                TotalReg = "0",
                                incluirConfirmacion = false, incluirCobro = false,
                                Familia = item.familia, PluAdbc = 90909090
                            )
                        }
                    }
                    // Solo marcamos introducidas cuando hay delta (no en COBRAR)
                    item.introducidas += nuevas
                    item.propsIntroducidas = item.propiedades.size
                }
            }
            if (lineasPend.isNotEmpty()) enviarLineasEnSerie(dbId, idPedido, lineasPend)
        }

        // 3) Limpieza visual inmediata (el borrado de Pend se hace tras terminar COBRAR)
        limpiarVistaDeTicket()
        pedidoViewModel.liberarPantallaActual()
        val msg = when (modo) {
            ModoEnvio.PENDIENTE -> "Pendiente enviado"
            ModoEnvio.IMPRIMIR -> "Impresión enviada"
            ModoEnvio.COBRAR -> "Cobro enviado"
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    /**  Producto base: cantidad – nombre – precio  */
    @SuppressLint("SetTextI18n")
    private fun filaPrincipal(item: ItemPedido): View {
        // Necesitamos estos valores para la llamada
        val idPedido =
            pedidoViewModel.obtenerIdPedidoMesaSeleccionada() ?: return View(requireContext())
        val dbId = sharedPref.getString("dbId", "cloud")!!

        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                MATCH_PARENT, WRAP_CONTENT
            ).apply { setMargins(0, 4, 0, 4) }
            setPadding(8, 8, 8, 8)
            setBackgroundResource(android.R.drawable.list_selector_background)

            // click corto: selecciona para propiedades
            setOnClickListener {
                ultimoItemSeleccionado = item
            }

            // long click: borrar N unidades
            setOnLongClickListener {
                // NumberPicker para elegir cuántas unidades borrar
                val picker = NumberPicker(requireContext()).apply {
                    minValue = 1
                    maxValue = item.cantidad
                    value = 1
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("¿Cuántas unidades borrar?")
                    .setView(picker)
                    .setPositiveButton("Borrar") { _, _ ->
                        val toRemove = picker.value
                        // 1) Actualizamos el ViewModel
                        pedidoViewModel.reducirItem(item, toRemove)
                        //También vaciamos la lista de propiedades si el item desaparece
                        if (item.cantidad - toRemove <= 0) {
                            item.propiedades.clear()
                        }
                        actualizarUIProductos()
                        Log.d("hola", toRemove.toString())
                        RetrofitClient.apiService
                            .borrarPedido(dbId, idPedido, item.plu.toString(), toRemove)
                            .enqueue(logCallback("delete", item.plu, idPedido))
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()

                true
            }

            // Ahora añadimos los tres elementos: cantidad, nombre y precio
            addView(TextView(context).apply {
                text = "${item.cantidad} x"; textSize = 16f; setTextColor(Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                    .apply { marginEnd = 8 }
            })
            addView(TextView(context).apply {
                text = item.nombreBase; textSize = 16f; setTextColor(Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = "%.2f €".format(item.precio); textSize = 16f; setTextColor(Color.DKGRAY)
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            })
        }
    }


    /**  Línea para cada COMBINADO  */
    private fun filaCombinado(item: ItemPedido) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(48, 2, 0, 2) }      // <-- sangría
        setPadding(8, 4, 8, 4)

        // cantidad & nombre
        addView(TextView(context).apply {
            text = "${item.cantidad} x ${item.nombreBase}"
            textSize = 14f; setTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        })
        // precio
        addView(TextView(context).apply {
            text = "%.2f €".format(item.precio)
            textSize = 14f; setTextColor(Color.GRAY)
        })
    }

    /**  Propiedad clásica • texto  */
    private fun filaProp(txt: String) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(48, 2, 0, 2) }      // misma sangría
        setPadding(8, 4, 8, 4)

        addView(TextView(context).apply {
            text = "•"; textSize = 14f; setTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8 }
        })
        addView(TextView(context).apply {
            text = txt; textSize = 14f; setTextColor(Color.GRAY)
        })
    }

}

/**  Combos: se identifican por su pluAdbc == 90909090  */
private fun ItemPedido.esCombinado() = this.pluadbc == 90909090

// Helper para logging uniformizado
private fun logCallback(tag: String, plu: Int, reg: String) =
    object : Callback<Void> {
        override fun onResponse(call: Call<Void>, response: Response<Void>) {
            if (response.isSuccessful) {
                Log.i("API", "✅ $tag (plu=$plu) guardado en reg=$reg")
            } else {
                val body = response.errorBody()?.string()
                Log.e("API", "❌ $tag (plu=$plu) ERROR ${response.code()} body=$body")
            }
        }

        override fun onFailure(call: Call<Void>, t: Throwable) {
            Log.e("API", "⚠️ Fallo $tag (plu=$plu) en reg=$reg", t)
        }
    }

private fun enviarLineasEnSerie(
    dbId: String,
    reg: String,
    lineas: List<Pedido>,
    index: Int = 0,
    onFinish: (() -> Unit)? = null
) {
    if (index >= lineas.size) {
        onFinish?.invoke()
        return
    }

    RetrofitClient.apiService
        .sincronizarPedido(dbId, reg, lineas[index])
        .enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                enviarLineasEnSerie(dbId, reg, lineas, index + 1, onFinish)
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                // seguimos para no “atascar” el resto
                enviarLineasEnSerie(dbId, reg, lineas, index + 1, onFinish)
            }
        })
}

private fun applyAutoSize(
    btn: TextView,
    minSp: Int = 10,
    maxSp: Int = 16,
    stepSp: Int = 1,
    maxLines: Int = 2
) {
    TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
        btn,
        minSp, maxSp, stepSp,
        TypedValue.COMPLEX_UNIT_SP
    )
    btn.isSingleLine = maxLines == 1
    btn.maxLines = maxLines
    btn.ellipsize = TextUtils.TruncateAt.END
    // Opcional: apurar alto
    btn.includeFontPadding = false
}

// Helper: parsear precio con coma/punto
private fun parsePrice(s: String?): Double =
    s?.replace(",", ".")?.toDoubleOrNull() ?: 0.0

// Devuelve el precio del producto para la tarifa indicada
private fun precioPorTarifa(producto: Producto, tarifaKey: String): Double = when (tarifaKey) {
    "Tarifa1" -> parsePrice(producto.Tarifa1)
    "Tarifa2" -> parsePrice(producto.Tarifa2)
    "Tarifa3" -> parsePrice(producto.Tarifa3)
    "Tarifa4" -> parsePrice(producto.Tarifa4)
    "Tarifa5" -> parsePrice(producto.Tarifa5)
    "Tarifa6" -> parsePrice(producto.Tarifa6)
    "Tarifa7" -> parsePrice(producto.Tarifa7)
    "Tarifa8" -> parsePrice(producto.Tarifa8)
    "Tarifa9" -> parsePrice(producto.Tarifa9)
    "Tarifa10" -> parsePrice(producto.Tarifa10)
    "Tarifa11" -> parsePrice(producto.Tarifa11) // (chupito si lo usas como base)
    "Tarifa12" -> parsePrice(producto.Tarifa12)
    "Tarifa13" -> parsePrice(producto.Tarifa13) // tapa
    "Tarifa14" -> parsePrice(producto.Tarifa14) // media
    "Tarifa15" -> parsePrice(producto.Tarifa15) // combinado
    else -> parsePrice(producto.Tarifa1)
}

// Por si el backend te devuelve "Tarifa3", "tarifa3" o solo "3"
private fun normalizaTarifaKey(key: String?): String {
    if (key.isNullOrBlank()) return "Tarifa1"
    val k = key.trim()
    return if (k.startsWith("Tarifa", ignoreCase = true)) {
        // Asegura "Tarifa" + número
        "Tarifa" + k.removePrefix("Tarifa").removePrefix("tarifa")
    } else {
        // Si viene "3", lo convertimos a "Tarifa3"
        "Tarifa$k"
    }
}
