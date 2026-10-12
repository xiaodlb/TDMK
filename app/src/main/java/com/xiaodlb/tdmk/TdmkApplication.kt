package com.xiaodlb.tdmk

import android.app.Application
import com.xiaodlb.tdmk.data.DatabaseProvider

class TdmkApplication : Application() {
    // Constructing the provider does not open SQLite on the main thread.
    val databaseProvider: DatabaseProvider by lazy { DatabaseProvider(this) }
}
