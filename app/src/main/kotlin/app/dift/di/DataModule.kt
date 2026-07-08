package app.dift.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import app.dift.data.db.DiftDatabase
import app.dift.data.db.dao.BlockEventDao
import app.dift.data.db.dao.GrantDao
import app.dift.data.db.dao.RuleDao
import app.dift.data.db.dao.UsageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DiftDatabase =
        Room.databaseBuilder(context, DiftDatabase::class.java, "dift.db").build()

    @Provides
    fun provideUsageDao(db: DiftDatabase): UsageDao = db.usageDao()

    @Provides
    fun provideRuleDao(db: DiftDatabase): RuleDao = db.ruleDao()

    @Provides
    fun provideGrantDao(db: DiftDatabase): GrantDao = db.grantDao()

    @Provides
    fun provideBlockEventDao(db: DiftDatabase): BlockEventDao = db.blockEventDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore
}
