package com.example.tpv

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import com.example.tpv.viewModels.LocalesViewModel
import es.redsys.paysys.Utils.Log
import android.widget.AdapterView

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: LocalesViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val idLocalEditText = findViewById<EditText>(R.id.tokenInput)
        val spinnerTerminales = findViewById<Spinner>(R.id.idInput)
        val spinnerDB = findViewById<Spinner>(R.id.spinner2)
        val textViewLocal = findViewById<TextView>(R.id.textView2)
        val button = findViewById<Button>(R.id.button1)
        val prefs = getSharedPreferences("TPV_PREFS", MODE_PRIVATE)

        // Define your DB options here
        val dbOptions = listOf("local", "cloud")

        // Create adapter and assign to spinner
        val dbAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, dbOptions)
        spinnerDB.adapter = dbAdapter

        val savedDbId = prefs.getString("dbId", "cloud")

        val selectedIndex = dbOptions.indexOf(savedDbId)
        if (selectedIndex >= 0) {
            spinnerDB.setSelection(selectedIndex)
        }

        spinnerDB.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedDb = parent.getItemAtPosition(position).toString()
                prefs.edit { putString("dbId", selectedDb) }
                Log.d("MainActivity", "Selected DB changed to: $selectedDb")
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                prefs.edit { putString("dbId", "cloud") }
            }
        }

        viewModel = ViewModelProvider(this)[LocalesViewModel::class.java]

        // Carga todos los locales una sola vez
        viewModel.cargarLocales(prefs.getString("dbId", "cloud").toString())

        viewModel.localSeleccionado.observe(this) { local ->
            if (local != null) {
                textViewLocal.text = local.nombre_local

                // 🔐 Guardar local en SharedPreferences desde la Activity (válido aquí)
                prefs.edit {
                    putInt("local_id", local.id_local)
                        .putString("local_nombre", local.nombre_local)
                    apply()
                }
            } else {
                textViewLocal.text = "Local no encontrado"
            }
        }

        viewModel.terminales.observe(this) { terminales ->
            val nombres = terminales.map { it.nombre_terminal }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, nombres)
            spinnerTerminales.adapter = adapter

            // 🔐 Guardar primer terminal si existe
            if (terminales.isNotEmpty()) {
                Log.d("terminales", nombres.toString())
                prefs.edit {
                    putString("terminal_nombre", terminales[0].nombre_terminal)
                }
            } else {
                Log.d("terminales", "error con los terminales")
            }
        }

        idLocalEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.seleccionarLocalPorIdTexto(prefs.getString("dbId", "cloud").toString(), s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        button.setOnClickListener {
            val idLocal = prefs.getInt("local_id", -1)
            val terminalSeleccionado = spinnerTerminales.selectedItem?.toString()

            if (idLocal != -1 && !terminalSeleccionado.isNullOrEmpty()) {
                // Guardar terminal si no lo habías guardado antes
                prefs.edit { putString("terminal", terminalSeleccionado) }
                val myIntent = Intent(this@MainActivity, DateLogActivity::class.java)
                this@MainActivity.startActivity(myIntent)
            } else {
                Toast.makeText(this, "Debes seleccionar un local y terminal válidos", Toast.LENGTH_SHORT).show()
            }

            //Inicializo la API del TPV con el codigo de licencia
           /* RedCLSConfigurationLibrary.setAppLicense("057678260");

            lifecycleScope.launch {
               try {
                   val response = withContext(Dispatchers.IO) {
                       RedCLSMerchantConfigurationManager.loginWithoutUser(this@MainActivity)
                   }

                   Log.e("LoginDebug", "response.class = ${response::class.java.name}")
                   Log.e("LoginDebug", "response.desc = ${response.desc}")

                   if (response.code == 0) {
                       // Login correcto
                       //val merchantList = response.merchantList
                       // Aquí puedes mostrar los comercios y terminales disponibles

                       // Navegar a la siguiente actividad en el hilo principal
                       val myIntent = Intent(this@MainActivity, DateLogActivity::class.java)
                       startActivity(myIntent)
                   } else {
                       // Error en login, mostrar mensaje en UI
                       val errorMsg = response.desc ?: "Error desconocido"
                       Log.e("LoginDebug", "Error en login: $errorMsg")
                       Toast.makeText(this@MainActivity, "Error en login: $errorMsg ${response.code}", Toast.LENGTH_LONG).show()
                   }
               } catch (e: Exception) {
                   Log.e("LoginDebug", "Excepción: ${e.message}")
                   Toast.makeText(this@MainActivity, "Excepción: ${e.message}", Toast.LENGTH_LONG).show()
               }
           }*/
        }
    }
}