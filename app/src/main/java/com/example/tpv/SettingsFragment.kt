package com.example.tpv

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editTextIdDispositivo = view.findViewById<EditText>(R.id.editTextIdDispositivo)
        val editTextToken = view.findViewById<EditText>(R.id.editTextToken)
        val editTextUrlApi = view.findViewById<EditText>(R.id.editTextUrlApi)
        val textEmpleado = view.findViewById<TextView>(R.id.textEmpleado)
        val buttonAplicar = view.findViewById<Button>(R.id.buttonAplicar)

        buttonAplicar.setOnClickListener {
            val id = editTextIdDispositivo.text.toString()
            val token = editTextToken.text.toString()
            val url = editTextUrlApi.text.toString()

            Toast.makeText(requireContext(), "Configuración guardada", Toast.LENGTH_SHORT).show()
            // Aquí puedes guardar estos datos en SharedPreferences o enviarlos a una API.
        }
    }
}