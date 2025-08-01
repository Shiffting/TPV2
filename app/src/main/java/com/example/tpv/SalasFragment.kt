package com.example.tpv

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.gridlayout.widget.GridLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.material.button.MaterialButton
import com.example.tpv.data.model.Producto
import com.example.tpv.data.api.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.tpv.data.model.Sala
import com.example.tpv.viewModels.PedidoViewModel
import com.example.tpv.viewModels.ProductosViewModel


class SalasFragment : Fragment() {
    private var listener: MesaClickListener? = null

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
    ): View? {
        return inflater.inflate(R.layout.fragment_salas, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gridLayoutSalas = view.findViewById(R.id.gridLayoutSalas)
        gridLayoutMesas = view.findViewById(R.id.gridLayoutMesas)

        val prefs = requireContext().getSharedPreferences("TPV_PREFS", Context.MODE_PRIVATE)
        val local = prefs.getString("local_nombre", null).toString()

        viewModel.cargarSalas(prefs.getString("dbId", "cloud").toString(), local)

        viewModel.salas.observe(viewLifecycleOwner) { salas ->
            if (salas.isEmpty()) {
                Toast.makeText(requireContext(), "No hay salas disponibles", Toast.LENGTH_SHORT).show()
            }
            gridLayoutSalas.removeAllViews()
            gridLayoutMesas.removeAllViews()
            salaButtons.clear()
            mesaButtons.clear()

            salas.forEach { sala ->
                val salaButton = MaterialButton(requireContext()).apply {
                    text = sala.denominacion
                    isCheckable = true
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(8, 8, 8, 8)
                    }
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
        }

        pedidoViewModel.itemsPorMesa.observe(viewLifecycleOwner) {
            val salaSeleccionada = pedidoViewModel.salaSeleccionada.value ?: return@observe
            val salaActual = viewModel.salas.value?.find { it.denominacion == salaSeleccionada }
            if (salaActual != null) {
                mostrarMesas(salaActual)
            }
        }
    }

    private fun mostrarMesas(sala: Sala) {
        gridLayoutMesas.removeAllViews()
        mesaButtons.clear()

        val productosPorMesa = pedidoViewModel.itemsPorMesa.value ?: emptyMap()

        repeat(sala.numMesas) { index ->
            val mesaNombre = "Mesa ${index + 1}"
            val clave = "${sala.denominacion}-$mesaNombre"

            // Verifica si hay productos asociados → mesa ocupada
            val mesaOcupada = productosPorMesa[clave]?.isNotEmpty() == true

            val mesaButton = MaterialButton(requireContext()).apply {
                text = mesaNombre
                isCheckable = true
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }

                // Colores según ocupación
                val colorRes = if (mesaOcupada) R.color.occupied_table else R.color.free_table
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, colorRes))

                setOnClickListener {
                    selectExclusive(this, mesaButtons)
                    pedidoViewModel.seleccionarMesa(text.toString())
                    listener?.irAParrilla()
                    println("Mesa seleccionada: ${text}")
                }
            }

            mesaButtons.add(mesaButton)
            gridLayoutMesas.addView(mesaButton)
        }
    }

    private fun selectExclusive(selected: MaterialButton, buttons: List<MaterialButton>) {
            for (btn in buttons) {
                btn.isChecked = btn == selected
            }
        }

    private fun clearMesaSelection(mesaButtons: List<MaterialButton>) {
        for (btn in mesaButtons) {
            btn.isChecked = false
        }
    }
}
interface MesaClickListener {
    fun irAParrilla()
}
