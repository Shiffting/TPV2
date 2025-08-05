package com.example.tpv

import android.annotation.SuppressLint
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.properties.Delegates
import androidx.core.graphics.toColorInt
import com.example.tpv.data.model.ItemPedido
import com.example.tpv.data.model.Producto
import com.example.tpv.data.model.Propiedades
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate

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
    private var modoCombinados = false// justo debajo de `private var modoCombinados = false`
    private var modoPrimerosSegundos = false
    private var modoCombinado = false
    private var productoBaseParaCombinados: ItemPedido? = null

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

        val dbId     = sharedPref.getString("dbId", "cloud")!!
        val colorNet = sharedPref.getString("local_nombre", "")!!
        pedidoViewModel.cargarPedidosPendientesDesdeBD(dbId, colorNet)


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
        btnCombinado.visibility = View.GONE
        btnCombinado.text = "COMBINADOS"
        btnCombinado.setOnClickListener {
            if (!modoCombinado) {
                // Entramos en modo Combinados
                productoBaseParaCombinados = ultimoItemSeleccionado
                modoCombinado            = true
                btnCombinado.text        = "FIN"
                btnCombinado.alpha       = 1f
            } else {
                // Salimos del modo
                modoCombinado            = false
                productoBaseParaCombinados = null
                btnCombinado.text        = "COMBINADOS"
                btnCombinado.visibility  = View.GONE
            }
        }

        btnPrimeros = view.findViewById<Button>(R.id.btnPrimeros)
        btnPrimeros.text = "PRIMEROS"
        btnPrimeros.setOnClickListener {
            // alternamos el modo
            modoPrimerosSegundos = !modoPrimerosSegundos

            // determinamos el header y el texto del botón
            val headerName = if (modoPrimerosSegundos) "--PRIMEROS--" else "--SEGUNDOS--"
            btnPrimeros.text = if (modoPrimerosSegundos) "SEGUNDOS" else "PRIMEROS"

            // creamos un ItemPedido especial para el header
            val headerItem = ItemPedido(
                nombreBase   = headerName,
                nombre       = headerName,
                precio       = 0.0,
                cantidad     = 1,
                plu          = 90909090,      // pluAdbc para propiedades/headers
                familia      = "",            // sin familia
                consumoSolo  = "",            // según tu modelo
                impresora    = "6",             // tal y como pides
                ivaVenta     = "0",
                pluadbc      = 90909090,      // identifica “propiedad / header”
                propiedades  = mutableListOf()
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

    @SuppressLint("SetTextI18n")
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
                    // 1) Determinar tarifa y sufijo según modoCombinados
                    val tarifa   = if (modoCombinados) producto.Tarifa15 else producto.Tarifa1
                    val sufijo   = if (modoCombinados) "_Cb" else ""
                    val nombreCb = producto.Producto + sufijo

                    if (modoCombinado && productoBaseParaCombinados != null) {
                        // —–––– modo Combinados: generar un ÍTEM “propiedad” pero con tarifa15 —––––
                        val base = productoBaseParaCombinados!!
                        val nombreCb = "${producto.Producto}_Cb"
                        val price15 = producto.Tarifa15.replace(",",".").toDoubleOrNull() ?: 0.0

                        val combinadoItem = ItemPedido(
                            nombreBase   = producto.Producto,
                            nombre       = nombreCb,
                            precio       = price15,
                            cantidad     = 1,
                            plu          = producto.Plu,
                            familia      = producto.Familia,
                            consumoSolo  = producto.ConsumoSolo,
                            impresora    = producto.Impresora,
                            ivaVenta     = producto.IvaVenta,
                            pluadbc      = 90909090,
                            propiedades  = mutableListOf(),
                            tarifaUsada = "Tarifa15"
                        )

                        // Insertamos justo después del base:
                        pedidoViewModel.insertarItemDespues(base, combinadoItem)
                        actualizarUIProductos()
                    } else {
                        val item = ItemPedido(
                            nombreBase   = producto.Producto,
                            nombre      = nombreCb,
                            precio      = tarifa.replace(",",".").toDouble(),
                            cantidad    = 1,
                            plu         = producto.Plu,
                            familia     = producto.Familia,
                            consumoSolo = producto.ConsumoSolo,
                            impresora   = producto.Impresora,
                            ivaVenta    = producto.IvaVenta,
                            pluadbc = producto.PluAdbc,
                            propiedades  = mutableListOf(),
                            tarifaUsada = "Tarifa1"
                        )
                        pedidoViewModel.añadirItem(item)
                        ultimoItemSeleccionado = item
                        btnCombinado.visibility = View.VISIBLE
                        btnCombinado.alpha      = 0.8f
                        btnCombinado.text       = "COMBINADOS"
                    }
                    actualizarUIProductos()

                    // Comprobamos si hay PROPIEDADES disponibles de cualquier tipo
                    val tieneApiProp = producto.Tarifa11 != "0"          // “Chupito”
                    val tieneTapa   = producto.TextoBotonTapa != "-"
                    val tieneMedia  = producto.TextoBotonMediaRacion != "-"

                    btnPropiedades.visibility =
                        if (tieneApiProp || tieneTapa || tieneMedia) View.VISIBLE
                        else View.INVISIBLE

                    val dbId    = sharedPref.getString("dbId", "cloud")!!
                    val color   = sharedPref.getString("local_nombre", "")!!
                    RetrofitClient.apiService
                        .getPropiedades(dbId, producto.Familia, producto.Producto, color)
                        .enqueue(object : Callback<List<Propiedades>> {
                            override fun onResponse(
                                call: Call<List<Propiedades>>,
                                response: Response<List<Propiedades>>
                            ) {
                                if (response.isSuccessful && response.body().orEmpty().isNotEmpty()) {
                                    // ¡tenemos props en la API!
                                    btnPropiedades.visibility = View.VISIBLE
                                }
                            }
                            override fun onFailure(call: Call<List<Propiedades>>, t: Throwable) {
                                // opcional: log o toast
                            }
                        })
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
                val (nuevoPrecio, nuevaTarifaNombre) = when {
                    "Chupito"      in item.propiedades -> producto.Tarifa11.toDouble() to "Tarifa11"
                    "Combinado"    in item.propiedades -> producto.Tarifa15.toDouble() to "Tarifa15"
                    producto.TextoBotonTapa        in item.propiedades -> producto.Tarifa13.toDouble() to "Tarifa13"
                    producto.TextoBotonMediaRacion in item.propiedades -> producto.Tarifa14.toDouble() to "Tarifa14"
                    else -> tarifaBase to item.tarifaUsada
                }

                val extra = item.propiedades.sumOf { prop ->
                    when (prop) {
                        // si alguna de tus props API tuviera coste adicional distinto
                        // de la tarifa base, lo pondrías aquí…
                        else         -> 0.0
                    }
                }
                item.precio = nuevoPrecio + extra
                item.tarifaUsada = nuevaTarifaNombre

                // 5) Refrescamos la UI sin ocultar el botón
                actualizarUIProductos()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }


    @SuppressLint("SetTextI18n")
    private fun actualizarUIProductos() {
        layoutTicketItems.removeAllViews()

        val items     = pedidoViewModel.obtenerItems(mesaActual, salaActual)
        var total     = 0.0
        var baseIndex = -1           // índice del último “producto base”

        items.forEachIndexed { idx, it ->

            /* ---------- FILA “NORMAL” (productos base, cabeceras, etc.) ---------- */
            if (!it.esCombinado()) {
                baseIndex = idx                           // recordamos dónde está el padre
                layoutTicketItems.addView( filaPrincipal(it) )
            }
            /* ---------- FILA “COMBINADO”  (pluAdbc == 90909090) ------------------- */
            else {
                // se inserta sangrada justo debajo de su base
                layoutTicketItems.addView( filaCombinado(it) )
            }

            /* ---------- PROPIEDADES ---------- */
            it.propiedades.forEach { p -> layoutTicketItems.addView( filaProp(p) ) }

            total += it.precio * it.cantidad
        }

        view?.findViewById<TextView>(R.id.textTotal)
            ?.text = "Total: %.2f €".format(total)
    }

    private fun enviarPedidoPendiente(incluirConfirmacion: Boolean) {
        val items = pedidoViewModel.obtenerItems(mesaActual, salaActual)
        val nombreCam = sharedPref.getString("empleado_nombre", "CAMARERA_DESCONOCIDA") ?: "CAMARERA_DESCONOCIDA"
        val idUnicoPedido = pedidoViewModel.obtenerIdPedidoMesaSeleccionada() ?: return

        val now = LocalDateTime.now()
        val fechaActual = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val horaActual = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

        val lineas = mutableListOf<Pedido>()

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
                Producto = item.nombre,
                Cantidad = if (item.esCombinado()) "0" else item.cantidad.toString(),
                Pts = item.precio.toString(),
                ImpresoCli = 0,
                Tarifa = item.tarifaUsada,
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
                PluAdbc = item.pluadbc
            )

            Log.e("LINEA", "" + linea);

            lineas += linea


            // === 2) Una línea por cada propiedad ===
            if (!item.esCombinado()) {
                item.propiedades.forEach { prop ->
                    val producto = productosViewModel.productos.value
                        ?.firstOrNull { it.Plu == item.plu }

                    var propname = when {
                        // 1) Propiedad “Tapa” para este producto
                        producto?.TextoBotonTapa == prop -> {
                            "_" + producto.TextoBotonTapa
                        }

                        // 2) Propiedad “Media Ración” para este producto
                        producto?.TextoBotonMediaRacion == prop -> {
                            "_" + producto.TextoBotonMediaRacion
                        }

                        // 3) Propiedades de API
                        prop == "Chupito"   -> {
                            "_Chup"
                        }
                        prop == "Combinado" -> {
                            "_Cb"
                        }

                        // 4) Por defecto, tarifa base
                        else -> {
                            "_Pro"
                        }
                    }

                    propname = if (prop.endsWith(propname)) {
                        prop
                    } else {
                        prop + propname
                    }
                    item.nombre = propname

                    val lineaProp = Pedido(
                        reg              = idUnicoPedido,
                        Hora             = horaActual,
                        NombreCam        = nombreCam,
                        NombreFormaPago  = salaActual,
                        Fecha            = fechaActual,
                        FechaReg         = fechaActual,
                        Barra            = 1,
                        Terminal         = 1,
                        Plu              = item.plu,
                        Producto         = item.nombre,
                        Cantidad         = "0",
                        Pts              = "0",
                        ImpresoCli       = 0,
                        Tarifa           = "0",
                        CBarras          = terminal,
                        PagoPendiente   = mesaActual,
                        Comensales       = "1",
                        Consumo          = item.consumoSolo,
                        IDCLIENTE        = "0A_VENTA",
                        Impreso          = item.impresora,
                        NombTerminal     = nombreLocal,
                        IvaVenta         = item.ivaVenta,
                        Iva              = item.ivaVenta,
                        TotalReg         = "0",
                        incluirConfirmacion = incluirConfirmacion,
                        Familia          = item.familia,
                        PluAdbc          = 90909090
                    )

                    lineas += lineaProp
                }
            }
        }
        enviarLineasEnSerie(sharedPref.getString("dbId","cloud")!!, idUnicoPedido, lineas)
        limpiarVistaDeTicket()
        pedidoViewModel.liberarPantallaActual()
        Toast.makeText(requireContext(), "Productos enviados como pendientes", Toast.LENGTH_SHORT).show()
    }

    /**  Producto base: cantidad – nombre – precio  */
    private fun filaPrincipal(item: ItemPedido) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 4, 0, 4) }
        setPadding(8, 8, 8, 8)
        setBackgroundResource(android.R.drawable.list_selector_background)

        // breve click → seleccionar
        setOnClickListener {
            ultimoItemSeleccionado = item
            btnPropiedades.visibility =
                if (item.propiedades.isNotEmpty()) View.VISIBLE else View.GONE
        }

        // cantidad
        addView(TextView(context).apply {
            text = "${item.cantidad} x"
            textSize = 16f; setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8 }
        })
        // nombre
        addView(TextView(context).apply {
            text = item.nombreBase
            textSize = 16f; setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        // precio
        addView(TextView(context).apply {
            text = "%.2f €".format(item.precio)
            textSize = 16f; setTextColor(Color.DKGRAY)
        })
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
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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
    index: Int = 0
) {
    if (index >= lineas.size) return

    RetrofitClient.apiService
        .sincronizarPedido(dbId, reg, lineas[index])
        .enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                // pase lo que pase, disparamos la siguiente línea
                enviarLineasEnSerie(dbId, reg, lineas, index + 1)
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                // aquí podrías parar o reintentar, según tus necesidades
                enviarLineasEnSerie(dbId, reg, lineas, index + 1)
            }
        })
}