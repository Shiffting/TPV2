package com.example.tpv

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import com.example.tpv.viewModels.LocalesViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: LocalesViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Preferencias
        val prefs = getSharedPreferences("TPV_PREFS", MODE_PRIVATE)

        // 0) Si ya tenemos todo, saltamos
        val savedLocalId  = prefs.getInt("local_id", -1)
        val savedTerminal = prefs.getString("terminal", null)
        if (savedLocalId != -1 && !savedTerminal.isNullOrEmpty()) {
            startActivity(Intent(this, DateLogActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Vistas
        val idLocalEditText   = findViewById<EditText>(R.id.tokenInput)
        val spinnerTerminales = findViewById<Spinner>(R.id.idInput)
        val spinnerDB         = findViewById<Spinner>(R.id.spinner2)
        val textViewLocal     = findViewById<TextView>(R.id.textView2)
        val button            = findViewById<Button>(R.id.button1)

        // --- 1) Spinner de DB ---
        val dbOptions = listOf("local", "cloud")
        spinnerDB.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, dbOptions)
        // Selección previa
        prefs.getString("dbId", "cloud")?.let {
            val idx = dbOptions.indexOf(it)
            if (idx >= 0) spinnerDB.setSelection(idx)
        }

        spinnerDB.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val selectedDb = parent.getItemAtPosition(pos).toString()

                // Guardamos el dbId y **reseteamos** local y terminal
                prefs.edit {
                    putString("dbId", selectedDb)
                    remove("local_id")
                    remove("local_nombre")
                    remove("terminal")
                }
                Log.d("MainActivity", "DB cambiado a: $selectedDb -- reiniciando selecciones de local/terminal")

                // Limpiamos UI de local/terminal
                idLocalEditText.text.clear()
                textViewLocal.text = ""
                spinnerTerminales.adapter = null

                // Recargamos locales de la nueva DB
                viewModel.cargarLocales(selectedDb)
            }

            override fun onNothingSelected(parent: AdapterView<*>) { /* no-op */ }
        }

        // --- 2) Configurar ViewModel ---
        viewModel = ViewModelProvider(this)[LocalesViewModel::class.java]
        // Carga inicial (por si ya había dbId guardado)
        viewModel.cargarLocales(prefs.getString("dbId", "cloud").orEmpty())

        viewModel.localSeleccionado.observe(this) { local ->
            if (local != null) {
                textViewLocal.text = local.nombre_local
                prefs.edit {
                    putInt("local_id", local.id_local)
                    putString("local_nombre", local.nombre_local)
                }
            } else {
                textViewLocal.text = "Local no encontrado"
            }
        }

        // --- 3) Spinner de terminales ---
        viewModel.terminales.observe(this) { terms ->
            val names = terms.map { it.nombre_terminal }
            spinnerTerminales.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)

            // Si acabamos de cambiar DB y no hay terminal guardada, preseleccionamos la primera
            if (prefs.getString("terminal", null).isNullOrEmpty() && terms.isNotEmpty()) {
                prefs.edit { putString("terminal", terms[0].nombre_terminal) }
            }
        }

        // --- 4) Filtrar locales según ID escrito ---
        idLocalEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.seleccionarLocalPorIdTexto(
                    prefs.getString("dbId", "cloud").orEmpty(),
                    s.toString()
                )
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // --- 5) Botón Continuar ---
        button.setOnClickListener {
            val localId = prefs.getInt("local_id", -1)
            val term    = spinnerTerminales.selectedItem?.toString()
            if (localId != -1 && !term.isNullOrEmpty()) {
                prefs.edit { putString("terminal", term) }
                startActivity(Intent(this, DateLogActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Selecciona un local y terminal válidos", Toast.LENGTH_SHORT).show()
            }
        }
    }
}