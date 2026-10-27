package com.example.nemebudget
import android.app.Application
import com.example.nemebudget.db.AppDatabase
import com.example.nemebudget.llm.LlmPipeline
import com.example.nemebudget.repository.AppRepository
import com.example.nemebudget.repository.RealRepository

class NemeApp : Application() {
    val pipeline: LlmPipeline by lazy { LlmPipeline(this) }

    val repository: AppRepository by lazy {
        RealRepository(this, AppDatabase.getDatabase(this), pipeline)
    }
}