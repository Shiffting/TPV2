package com.example.tpv

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CalendarView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.tpv.viewModels.EmpleadosViewModel

class DateLogActivity : AppCompatActivity() {

    private lateinit var spinner: Spinner
    private lateinit var empleadosViewModel: EmpleadosViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_date_log)

        val calendar = findViewById<CalendarView>(R.id.calendarView)
        calendar.setDate(System.currentTimeMillis(), false, true)
        calendar.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val date = "$dayOfMonth/${month + 1}/$year"
            println("Fecha seleccionada: $date")
        }

        spinner = findViewById(R.id.spinner)
        val button = findViewById<Button>(R.id.button1)

        // Obtener id_local del SharedPreferences
        val sharedPref = getSharedPreferences("TPV_PREFS", MODE_PRIVATE)
        val idLocal = sharedPref.getInt("local_id", -1)

        // ViewModel
        empleadosViewModel = ViewModelProvider(this).get(EmpleadosViewModel::class.java)

        if (idLocal != -1) {
            empleadosViewModel.cargarEmpleados(sharedPref.getString("dbId", "cloud").toString(), idLocal)
        } else {
            Toast.makeText(this, "No se encontró el local $idLocal", Toast.LENGTH_SHORT).show()
        }

        empleadosViewModel.empleados.observe(this) { empleados ->
            val nombres = empleados.map { it.nombre_camarero }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, nombres)
            spinner.adapter = adapter
        }

        button.setOnClickListener {
            // Puedes guardar el empleado seleccionado si lo necesitas:
            val empleadoSeleccionado = spinner.selectedItem.toString()
            // Guardar en SharedPreferences
            val sharedPref = getSharedPreferences("TPV_PREFS", MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("empleado_nombre", empleadoSeleccionado)
                apply()
            }
            println("Empleado seleccionado: $empleadoSeleccionado")
            val myIntent = Intent(this@DateLogActivity, TPVActivity::class.java)
            startActivity(myIntent)
        }
    }
}
