package com.example.tpv

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.GridLayout
import android.widget.NumberPicker
import com.example.tpv.data.model.FamiliaProducto
import com.example.tpv.viewModels.PedidoViewModel
import com.example.tpv.viewModels.ProductosViewModel
import com.google.android.material.button.MaterialButton
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.properties.Delegates
import androidx.core.graphics.toColorInt
import com.example.tpv.data.model.ItemPedido
import com.example.tpv.data.model.Producto
import com.example.tpv.data.model.Propiedades
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ParrillaFragment : Fragment() {
    private val pedidoViewModel: PedidoViewModel by activityViewModels()
    private val productosViewModel: ProductosViewModel by activityViewModels()
    private lateinit var layoutCategorias: GridLayout
    private lateinit var layoutProductos: GridLayout
    private lateinit var layoutTicketItems: LinearLayout
    private var mesaActual: String = "sin mesa"
    private var salaActual: String = "sin sala"
    private var idLocal by Delegates.notNull<Int>()
    private lateinit var nombreLocal:String
    private lateinit var terminal:String
    private lateinit var camarero:String
    private val handler = Handler(Looper.getMainLooper())
    private var refrescoActivo = false
    private var productosCargados = false
    private var pedidosCargados = false
    private lateinit var btnPrimeros:Button
    private lateinit var btnPropiedades:Button
    private lateinit var btnCombinado:Button
    private lateinit var sharedPref: SharedPreferences
    private var ultimoItemSeleccionado: ItemPedido? = null
    private val productoOriginalMap = mutableMapOf<Int, Producto>()
    private var lastKey = ""
    private var lastItems = emptyList<ItemPedido>()

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


        layoutCategorias = view.findViewById(R.id.layoutCategorias)
        layoutProductos = view.findViewById(R.id.layoutProductos)
        layoutTicketItems = view.findViewById(R.id.layoutTicketItems)

        // Cargar datos del ViewModel (que a su vez los carga de la base de datos)
        productosViewModel.cargarFamilias(sharedPref.getString("dbId", "cloud").toString(), nombreLocal)
        productosViewModel.cargarProductos(sharedPref.getString("dbId", "cloud").toString(), nombreLocal)

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

            actualizarUIProductos()
        }

        val btnPendiente = view.findViewById<Button>(R.id.btnPendiente)
        btnPendiente.setOnClickListener {
            enviarPedidoPendiente(incluirConfirmacion = false)
        }

        val btnImprimir = view.findViewById<Button>(R.id.btnImprimir)
        btnImprimir.setOnClickListener {
            enviarPedidoPendiente(incluirConfirmacion = true)
        }

        val btnCobrar = view.findViewById<Button>(R.id.btnCobrar)
        btnCobrar.visibility = View.INVISIBLE
        btnPropiedades = view.findViewById<Button>(R.id.btnPropiedades)
        btnPropiedades.visibility = View.INVISIBLE
        btnPropiedades.setOnClickListener {
            val item = ultimoItemSeleccionado ?: return@setOnClickListener
            val productoOriginal = productoOriginalMap[item.plu] ?: return@setOnClickListener
            val colorNet = sharedPref.getString("local_nombre", "")!!

            // 2) Hacer la llamada
            RetrofitClient.apiService.getPropiedades(sharedPref.getString("dbId", "cloud").toString(), productoOriginal.Familia, productoOriginal.Producto, colorNet)
                .enqueue(object : Callback<List<Propiedades>> {
                    override fun onResponse(call: Call<List<Propiedades>>, response: Response<List<Propiedades>>) {
                        val props = response.body().orEmpty()
                        Log.d("Parrilla", "Propiedades recibidas: ${props.size} ${productoOriginal.Familia} ${productoOriginal.Producto} ${colorNet}")
                        showPropiedadesDialog(props, productoOriginal, ultimoItemSeleccionado!!)
                    }
                    override fun onFailure(
                        call: Call<List<Propiedades>>,
                        t: Throwable
                    ) {
                        Toast.makeText(context, "Error al cargar propiedades", Toast.LENGTH_SHORT).show()
                    }
                })
        }
        btnCombinado = view.findViewById<Button>(R.id.btnCombinado)
        btnCombinado.setOnClickListener {

        }
        btnPrimeros = view.findViewById<Button>(R.id.btnPrimeros)
        btnPrimeros.setOnClickListener {

        }
        iniciarRefrescoMesas()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        refrescoActivo = false
        handler.removeCallbacksAndMessages(null)
    }


    private fun iniciarRefrescoMesas() {
        refrescoActivo = true
        handler.post(object : Runnable {
            override fun run() {
                if (refrescoActivo) {
                    val dbId = sharedPref.getString("dbId", "cloud") ?: "cloud"
                    val colorNet = sharedPref.getString("local_nombre", "") ?: ""

                    pedidoViewModel.cargarPedidosPendientesDesdeBD(dbId, colorNet)

                    // Refrescar cada 60 segundos
                    handler.postDelayed(this, 60_000)
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

        familias.filter { it.VISIBLETPV == 1}.sortedBy { it.NOrden }.forEach { familia ->
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

    private fun mostrarProductosDeFamilia(nombreFamilia: String) {
        Log.d("producto", productosViewModel.productos.value.toString())
        val productos = productosViewModel.productos.value
            ?.filter { it.Familia == nombreFamilia && it.VISIBLETPV == 1 && it.ColorNet == nombreLocal}
            ?.sortedBy { it.Orden.toIntOrNull() ?: 0}
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
                isAllCaps = false
                setPadding(12, 24, 12, 24)
                gravity = Gravity.CENTER
                textSize = 14f
                setBackgroundColor("#2196F3".toColorInt()) // Naranja
                setOnClickListener {
                    producto.precioSeleccionado = producto.Tarifa1
                    val item = ItemPedido(
                        nombreBase   = producto.Producto,
                        nombre      = producto.Producto,
                        precio      = producto.Tarifa1.replace(",", ".").toDouble(),
                        cantidad    = 1,
                        plu         = producto.Plu,
                        familia     = producto.Familia,
                        consumoSolo = producto.ConsumoSolo,
                        impresora   = producto.Impresora,
                        ivaVenta    = producto.IvaVenta,
                        pluadbc = producto.PluAdbc,
                        propiedades  = mutableListOf()
                    )
                    pedidoViewModel.añadirItem(item)
                    ultimoItemSeleccionado = item
                    actualizarUIProductos()

                    // Comprobamos si hay PROPIEDADES disponibles de cualquier tipo
                    val tieneApiProp = producto.Tarifa11 != "0"          // “Chupito”
                    val tieneTapa   = producto.TextoBotonTapa != "-"
                    val tieneMedia  = producto.TextoBotonMediaRacion != "-"

                    btnPropiedades.visibility =
                        if (tieneApiProp || tieneTapa || tieneMedia) View.VISIBLE
                        else View.INVISIBLE
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
            if (producto.TextoBotonTapa != "-")
                add(producto.TextoBotonTapa)
            if (producto.TextoBotonMediaRacion != "-")
                add(producto.TextoBotonMediaRacion)
        }

        // 2) Preparamos el array de checados basado en item.propiedades
        val checked = BooleanArray(allLabels.size) { idx ->
            item.propiedades.contains(allLabels[idx])
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Propiedades para ${item.nombreBase}")
            .setMultiChoiceItems(allLabels.toTypedArray(), checked) { _, which, isChecked ->
                // 3) Al marcar/desmarcar, actualizamos la lista EN VIVO
                val label = allLabels[which]
                if (isChecked) {
                    if (!item.propiedades.contains(label))
                        item.propiedades.add(label)
                } else {
                    item.propiedades.remove(label)
                }
            }
            .setPositiveButton("OK") { _, _ ->
                // Nombre base + lista de propiedades
                val tarifaBase = producto.Tarifa1.replace(",",".").toDoubleOrNull() ?: 0.0
                val reemplaza = when {
                    "Chupito"      in item.propiedades -> producto.Tarifa11.toDouble()
                    "Combinado"    in item.propiedades -> producto.Tarifa15.toDouble()
                    producto.TextoBotonTapa        in item.propiedades -> producto.Tarifa13.toDouble()
                    producto.TextoBotonMediaRacion in item.propiedades -> producto.Tarifa14.toDouble()
                    else -> null
                }
                val extra = item.propiedades.sumOf { prop ->
                    when (prop) {
                        // si alguna de tus props API tuviera coste adicional distinto
                        // de la tarifa base, lo pondrías aquí…
                        else         -> 0.0
                    }
                }
                item.precio = (reemplaza ?: tarifaBase) + extra

                // 5) Refrescamos la UI sin ocultar el botón
                actualizarUIProductos()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }


    @SuppressLint("SetTextI18n")
    private fun actualizarUIProductos() {
        layoutTicketItems.removeAllViews()
        val items = pedidoViewModel.obtenerItems(mesaActual, salaActual)
        val idPedido = pedidoViewModel.obtenerIdPedidoMesaSeleccionada() ?: ""
        var total = 0.0

        for (item in items) {
            // --- 1) Fila principal (clickable para borrar/reducir) ---
            val subtotal = item.precio * item.cantidad
            total += subtotal
            val prodOrig = productoOriginalMap[item.plu]
                ?: throw IllegalStateException("No hay Producto para plu=${item.plu}")

            val itemLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    MATCH_PARENT, WRAP_CONTENT
                ).apply { setMargins(0, 4, 0, 4) }
                setPadding(8, 8, 8, 8)
                isClickable = true
                isFocusable = true
                tag = item
                setBackgroundResource(android.R.drawable.list_selector_background)
                // Sólo selección/propiedades en click
                setOnClickListener {
                    ultimoItemSeleccionado = item
                    btnPropiedades.visibility =
                        if (prodOrig.tieneConfiguracion()) View.VISIBLE
                        else View.INVISIBLE
                }
                setOnLongClickListener {
                    val picker = NumberPicker(requireContext()).apply {
                        minValue = 1
                        maxValue = item.cantidad
                        value    = 1
                    }
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("¿Cuántas unidades borrar?")
                        .setView(picker)
                        .setPositiveButton("Borrar") { _, _ ->
                            val toRemove = picker.value
                            pedidoViewModel.reducirItem(item, toRemove)
                            RetrofitClient.apiService.borrarPedido(
                                sharedPref.getString("dbId","cloud")!!,
                                idPedido,
                                item.plu.toString()
                            ).enqueue(object: Callback<Void> {
                                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                                    if (response.isSuccessful) {
                                        Log.i("API", "✅ Unidad borrada de pedido $idPedido (plu=${item.plu})")
                                    } else {
                                        Log.e("API", "❌ Error borrando unidad del pedido $idPedido (plu=${item.plu}) HTTP ${response.code()}")
                                    }
                                }
                                override fun onFailure(call: Call<Void>, t: Throwable) {
                                    Log.e("API", "Fallo al borrar unidad del pedido $idPedido (plu=${item.plu})", t)
                                }
                            })
                            actualizarUIProductos()
                        }
                        .setNegativeButton("No", null)
                        .show()
                    true  // consume el evento
                }
            }

            // Cantidad
            val cantidadView = TextView(context).apply {
                text = "${item.cantidad} x"
                setTextColor(Color.BLACK); textSize = 16f
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                    .apply { marginEnd = 8 }
            }
            // Nombre
            val nombreView = TextView(context).apply {
                text = item.nombre
                setTextColor(Color.BLACK); textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            }
            // Precio unitario
            val precioView = TextView(context).apply {
                text = "%.2f €".format(item.precio)
                setTextColor(Color.DKGRAY); textSize = 16f
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            }

            itemLayout.addView(cantidadView)
            itemLayout.addView(nombreView)
            itemLayout.addView(precioView)
            layoutTicketItems.addView(itemLayout)

            // --- 2) Líneas de propiedades (no clickable) ---
            item.propiedades.forEach { prop ->
                val propLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        MATCH_PARENT, WRAP_CONTENT
                    ).apply { setMargins(48, 2, 0, 2) }
                    setPadding(8, 4, 8, 4)
                }
                val bullet = TextView(context).apply {
                    text = "•"
                    setTextColor(Color.GRAY); textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                        .apply { marginEnd = 8 }
                }
                val propView = TextView(context).apply {
                    text = prop
                    setTextColor(Color.GRAY); textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                }
                propLayout.addView(bullet)
                propLayout.addView(propView)
                layoutTicketItems.addView(propLayout)
            }
        }

        // Total
        view?.findViewById<TextView>(R.id.textTotal)?.apply {
            text = "Total: %.2f €".format(total)
        }
    }


    private fun enviarPedidoPendiente(incluirConfirmacion: Boolean) {
        val items = pedidoViewModel.obtenerItems(mesaActual, salaActual)
        val nombreCam = sharedPref.getString("empleado_nombre", "CAMARERA_DESCONOCIDA") ?: "CAMARERA_DESCONOCIDA"
        val idUnicoPedido = pedidoViewModel.obtenerIdPedidoMesaSeleccionada() ?: return

        val now = LocalDateTime.now()
        val fechaActual = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val horaActual = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

        items.forEach { item ->
            val total = item.cantidad * item.precio

            Log.d("LINEA", item.toString());

            val linea = Pedido(
                reg = idUnicoPedido,
                Hora = horaActual,
                NombreCam = nombreCam,
                NombreFormaPago = salaActual,
                Fecha = fechaActual,
                FechaReg = fechaActual,
                Barra = 1,
                Terminal = 1,
                Plu = item.plu,
                Producto = item.nombreBase,
                Cantidad = item.cantidad.toString(),
                Pts = item.precio.toString(),
                ImpresoCli = 0,
                Tarifa = item.precio.toString(),
                CBarras = terminal,
                PagoPendiente = mesaActual,
                Comensales = "1",
                Consumo = item.consumoSolo,
                IDCLIENTE = "0A_VENTA",
                Impreso = item.impresora,
                NombTerminal = nombreLocal,
                IvaVenta = item.ivaVenta,
                Iva = item.ivaVenta,
                TotalReg = total.toString(),
                incluirConfirmacion = incluirConfirmacion,
                Familia = item.familia,
                PluAdbc = 0
            )

            Log.e("LINEA", "" + linea);

            RetrofitClient.apiService
                .sincronizarPedido(sharedPref.getString("dbId", "cloud").toString(), idUnicoPedido, linea)
                .enqueue(logCallback("base", item.plu, idUnicoPedido))


            // === 2) Una línea por cada propiedad ===
            item.propiedades.forEach { prop ->
                val producto = productosViewModel.productos.value
                    ?.firstOrNull { it.Plu == item.plu }

                val tarifaProp = when {
                    // 1) Propiedad “Tapa” para este producto
                    producto?.TextoBotonTapa == prop ->
                        productosViewModel.obtenerTarifaPorPlu(item.plu, 13)

                    // 2) Propiedad “Media Ración” para este producto
                    producto?.TextoBotonMediaRacion == prop ->
                        productosViewModel.obtenerTarifaPorPlu(item.plu, 14)

                    // 3) Propiedades de API
                    prop == "Chupito"   -> productosViewModel.obtenerTarifaPorPlu(item.plu, 11)
                    prop == "Combinado" -> productosViewModel.obtenerTarifaPorPlu(item.plu, 15)

                    // 4) Por defecto, tarifa base
                    else -> productosViewModel.obtenerTarifaPorPlu(item.plu, 1)
                }

                val totalProp = item.cantidad * tarifaProp

                val lineaProp = Pedido(
                    reg              = idUnicoPedido,
                    Hora             = horaActual,
                    NombreCam        = nombreCam,
                    NombreFormaPago  = salaActual,
                    Fecha            = fechaActual,
                    FechaReg         = fechaActual,
                    Barra            = 1,
                    Terminal         = 1,
                    Plu              = 90909090,
                    Producto         = prop,
                    Cantidad         = item.cantidad.toString(),
                    Pts              = tarifaProp.toString(),
                    ImpresoCli       = 0,
                    Tarifa           = tarifaProp.toString(),
                    CBarras          = terminal,
                    PagoPendiente   = mesaActual,
                    Comensales       = "1",
                    Consumo          = item.consumoSolo,
                    IDCLIENTE        = "0A_VENTA",
                    Impreso          = item.impresora,
                    NombTerminal     = nombreLocal,
                    IvaVenta         = item.ivaVenta,
                    Iva              = item.ivaVenta,
                    TotalReg         = totalProp.toString(),
                    incluirConfirmacion = incluirConfirmacion,
                    Familia          = item.familia,
                    PluAdbc          = 90909090
                )

                RetrofitClient.apiService
                    .sincronizarPedido(sharedPref.getString("dbId", "cloud").toString(), idUnicoPedido, lineaProp)
                    .enqueue(logCallback("prop", 90909090, idUnicoPedido))
            }
        }
        limpiarVistaDeTicket()
        pedidoViewModel.liberarPantallaActual()
        Toast.makeText(requireContext(), "Productos enviados como pendientes", Toast.LENGTH_SHORT).show()
    }
}

// Extensión de Producto para consultar si admite configuración de props
private fun Producto.tieneConfiguracion(): Boolean {
    val tieneApi    = this.Tarifa11  != "0"
    val tieneTapa   = this.TextoBotonTapa        != "-"
    val tieneMedia  = this.TextoBotonMediaRacion != "-"
    return tieneApi || tieneTapa || tieneMedia
}

// Helper para logging uniformizado
private fun logCallback(tag: String, plu: Int, reg: String) =
    object : Callback<Void> {
        override fun onResponse(call: Call<Void>, response: Response<Void>) {
            if (response.isSuccessful) Log.i("API", "✅ $tag (plu=$plu) guardado en reg=$reg")
            else                    Log.e("API", "❌ $tag (plu=$plu) ERROR ${response.code()}")
        }
        override fun onFailure(call: Call<Void>, t: Throwable) {
            Log.e("API", "Fallo $tag (plu=$plu) en reg=$reg", t)
        }
    }