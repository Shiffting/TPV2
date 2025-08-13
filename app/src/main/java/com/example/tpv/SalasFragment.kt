package com.example.tpv

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.gridlayout.widget.GridLayout
import com.example.tpv.data.model.Sala
import com.example.tpv.viewModels.PedidoViewModel
import com.example.tpv.viewModels.ProductosViewModel
import com.google.android.material.button.MaterialButton

class SalasFragment : Fragment() {

    private var listener: MesaClickListener? = null

    // Alturas mínimas homogéneas (puedes ajustar estos valores)
    private val BTN_MIN_HEIGHT_DP_SALA = 56
    private val BTN_MIN_HEIGHT_DP_MESA = 56

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MesaClickListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement MesaClickListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    private val pedidoViewModel: PedidoViewModel by activityViewModels()
    private val viewModel: ProductosViewModel by activityViewModels()

    private lateinit var gridLayoutSalas: GridLayout
    private lateinit var gridLayoutMesas: GridLayout

    private val salaButtons = mutableListOf<MaterialButton>()
    private val mesaButtons = mutableListOf<MaterialButton>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_salas, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gridLayoutSalas = view.findViewById(R.id.gridLayoutSalas)
        gridLayoutMesas = view.findViewById(R.id.gridLayoutMesas)

        val prefs: SharedPreferences =
            requireContext().getSharedPreferences("TPV_PREFS", Context.MODE_PRIVATE)
        val local = prefs.getString("local_nombre", null).orEmpty()
        val dbId  = prefs.getString("dbId", "cloud").orEmpty()

        viewModel.cargarSalas(dbId, local)

        viewModel.salas.observe(viewLifecycleOwner) { salas ->
            gridLayoutSalas.removeAllViews()
            gridLayoutMesas.removeAllViews()
            salaButtons.clear()
            mesaButtons.clear()

            if (salas.isEmpty()) {
                Toast.makeText(requireContext(), "No hay salas disponibles", Toast.LENGTH_SHORT).show()
                return@observe
            }

            salas.forEach { sala ->
                val salaButton = MaterialButton(requireContext()).apply {
                    text = sala.denominacion
                    isAllCaps = false
                    isCheckable = true
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(8, 8, 8, 8)
                    }

                    // Autosize 100% en código: 1 línea preferida, 2 líneas como fallback
                    applyAutoSizeOneLinePrefer(
                        button = this,
                        minSp = 9,   // tamaño mínimo
                        maxSp = 18,  // tamaño máximo
                        stepSp = 1,
                        fallbackToTwoLines = true,
                        minHeightPx = dpToPx(BTN_MIN_HEIGHT_DP_SALA)
                    )

                    setOnClickListener {
                        selectExclusive(this, salaButtons)
                        pedidoViewModel.seleccionarSala(sala.denominacion)
                        mostrarMesas(sala)
                        clearMesaSelection(mesaButtons)
                    }
                }

                salaButtons.add(salaButton)
                gridLayoutSalas.addView(salaButton)
            }

            // Selección inicial opcional
            salaButtons.firstOrNull()?.performClick()
        }

        // Refresca ocupación al cambiar items
        pedidoViewModel.itemsPorMesa.observe(viewLifecycleOwner) {
            val salaSeleccionada = pedidoViewModel.salaSeleccionada.value ?: return@observe
            val salaActual = viewModel.salas.value?.find { it.denominacion == salaSeleccionada } ?: return@observe
            mostrarMesas(salaActual)
        }
    }

    private fun mostrarMesas(sala: Sala) {
        gridLayoutMesas.removeAllViews()
        mesaButtons.clear()

        val productosPorMesa = pedidoViewModel.itemsPorMesa.value ?: emptyMap()
        val prefs = requireContext().getSharedPreferences("TPV_PREFS", Context.MODE_PRIVATE)

        sala.mesas.forEach { nombreMesa ->
            val clave = "${sala.denominacion}-$nombreMesa"
            val mesaOcupada = productosPorMesa[clave]?.isNotEmpty() == true
            val mesaImpresa = prefs.getBoolean("mesa_impresa_$clave", false)

            val mesaButton = MaterialButton(requireContext()).apply {
                text = nombreMesa
                isAllCaps = false
                isCheckable = true
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                val colorRes = when {
                    mesaImpresa -> R.color.check_requested_table   // naranja
                    mesaOcupada -> R.color.occupied_table         // rojo
                    else        -> R.color.free_table             // verde
                }
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, colorRes))

                // Igual lógica para botones de mesa
                applyAutoSizeOneLinePrefer(
                    button = this,
                    minSp = 10,
                    maxSp = 18,
                    stepSp = 1,
                    fallbackToTwoLines = true,
                    minHeightPx = dpToPx(BTN_MIN_HEIGHT_DP_MESA)
                )

                setOnClickListener {
                    selectExclusive(this, mesaButtons)
                    pedidoViewModel.seleccionarMesa(text.toString())
                    listener?.irAParrilla()
                }
            }

            mesaButtons.add(mesaButton)
            gridLayoutMesas.addView(mesaButton)
        }
    }

    private fun selectExclusive(selected: MaterialButton, buttons: List<MaterialButton>) {
        buttons.forEach { it.isChecked = (it == selected) }
    }

    private fun clearMesaSelection(buttons: List<MaterialButton>) {
        buttons.forEach { it.isChecked = false }
    }

    // ===== Helpers de autosize por CÓDIGO =====

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
    private fun applyAutoSizeOneLinePrefer(
        button: MaterialButton,
        minSp: Int,
        maxSp: Int,
        stepSp: Int,
        fallbackToTwoLines: Boolean,
        minHeightPx: Int
    ) {
        // Altura mínima homogénea directamente por código
        button.minHeight = minHeightPx
        button.minimumHeight = minHeightPx

        // Preferir 1 línea
        button.setSingleLine(true)
        button.maxLines = 1
        button.ellipsize = TextUtils.TruncateAt.END
        button.includeFontPadding = false

        // Auto-size uniforme por código
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            button, minSp, maxSp, stepSp, TypedValue.COMPLEX_UNIT_SP
        )

        // Revisión tras el layout: si a minSp sigue con elipsis, permite 2 líneas
        if (fallbackToTwoLines) {
            button.doOnLayout {
                val layout = button.layout ?: return@doOnLayout
                val hasEllipsisOnLine0 = layout.lineCount > 0 && layout.getEllipsisCount(0) > 0
                if (hasEllipsisOnLine0) {
                    button.setSingleLine(false)
                    button.maxLines = 2
                    button.ellipsize = TextUtils.TruncateAt.END
                    TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                        button, minSp, maxSp, stepSp, TypedValue.COMPLEX_UNIT_SP
                    )
                }
            }
        }
    }
}

interface MesaClickListener {
    fun irAParrilla()
}
