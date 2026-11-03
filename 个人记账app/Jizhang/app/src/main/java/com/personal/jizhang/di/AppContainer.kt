package com.personal.jizhang.di

import android.content.Context
import com.personal.jizhang.data.LedgerRepository
import com.personal.jizhang.data.backup.BackupService

class AppContainer(context: Context) {
    val repository = LedgerRepository(context.applicationContext)
    val backup = BackupService(context.applicationContext, repository)
}
