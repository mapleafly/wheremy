package com.wheremy.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Phase 0 的装配样本：证明 Hilt 的构造注入与 @Provides 都通了。 */
@Singleton
class Greeter
@Inject
constructor() {
    fun greet(): String = "Wheremy · Phase 0 骨架已跑通"
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideGreeter(): Greeter = Greeter()
}
