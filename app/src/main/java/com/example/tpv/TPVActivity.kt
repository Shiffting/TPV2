package com.example.tpv

import android.content.Context
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.tpv.viewModels.PedidoViewModel

class TPVActivity : AppCompatActivity(), MesaClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tpvactivity)

        val prefs = getSharedPreferences("TPV_PREFS", Context.MODE_PRIVATE)
        val dbId = prefs.getString("dbId", "cloud") ?: "cloud"
        val colorNet = prefs.getString("local_nombre", "") ?: ""

        val pedidoViewModel: PedidoViewModel by viewModels()

        // Cargar los pedidos pendientes desde la BD
        pedidoViewModel.cargarPedidosPendientesDesdeBD(dbId, colorNet)

        // Cargar fragmento inicial
        loadFragment(SalasFragment())

        // Setup menú
        findViewById<ImageButton>(R.id.btnSalas).setOnClickListener {
            loadFragment(SalasFragment())
        }
        findViewById<ImageButton>(R.id.btnParrilla).setOnClickListener {
            loadFragment(ParrillaFragment())
        }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            loadFragment(SettingsFragment())
        }
        findViewById<ImageButton>(R.id.btnDisconnect).setOnClickListener {
            //TODO disconnect
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.activity_content, fragment)
            .commit()
    }

    override fun irAParrilla() {
        loadFragment(ParrillaFragment())
    }

}
