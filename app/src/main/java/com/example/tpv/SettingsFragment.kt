package com.example.tpv

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.tpv.viewModels.EmpleadosViewModel

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val textEmpleado = view.findViewById<Spinner>(R.id.spinnerEmpleado)
        val empleadosViewModel = ViewModelProvider(this)[EmpleadosViewModel::class.java]
        // Preferencias
        val prefs = requireContext().getSharedPreferences("TPV_PREFS", Context.MODE_PRIVATE)
        val dbId = prefs.getString("dbId", "cloud")!!
        empleadosViewModel.cargarEmpleados(dbId, prefs.getInt("local_id", -1))


        empleadosViewModel.empleados.observe(viewLifecycleOwner) { empleados ->
            val nombres = empleados.map { it.nombre_camarero }

            // Adapter correcto para Spinner
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                nombres
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            textEmpleado.adapter = adapter

            // Pre-seleccionar el último empleado guardado (si existe)
            prefs.getString("empleado_nombre", null)?.let { guardado ->
                val idx = nombres.indexOf(guardado)
                if (idx >= 0) textEmpleado.setSelection(idx)
            }

            // Guardar cuando el usuario selecciona
            textEmpleado.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val seleccionado = nombres[position]
                    prefs.edit { putString("empleado_nombre", seleccionado) }
                }

                override fun onNothingSelected(parent: AdapterView<*>) { /* no-op */
                }
            }
        }
    }
}