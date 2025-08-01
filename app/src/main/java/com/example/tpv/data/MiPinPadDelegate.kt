package com.example.tpv.data

import android.content.Context
import android.util.Log
import es.redsys.paysys.Operative.Managers.RedCLSDccSelectionData
import es.redsys.paysys.Operative.Managers.RedCLSDeferPaymentData
import es.redsys.paysys.Operative.RedCLSPinPadInterface

class MiPinPadDelegate(context: Context?) : RedCLSPinPadInterface {
    private val ctx = context
    override fun getContext(): Context? = ctx

    override fun conexionPinPadRealizada() {
        Log.d("Pinpad", "¡Conexión exitosa!")
    }

    override fun pinPadNoEncontrado() {
        Log.e("Pinpad", "Pinpad no encontrado.")
    }

    override fun seleccionMonedaPagoDCC(p0: RedCLSDccSelectionData?): String = "978"
    override fun selectionDeferPayment(p0: RedCLSDeferPaymentData?): String = "0"
}
