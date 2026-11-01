package net.kikin.nubecita.core.profile.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.profile.DefaultProfileRecordWriter
import net.kikin.nubecita.core.profile.ProfileRecordWriter

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileRecordWriterModule {
    @Binds
    internal abstract fun bindProfileRecordWriter(impl: DefaultProfileRecordWriter): ProfileRecordWriter
}
