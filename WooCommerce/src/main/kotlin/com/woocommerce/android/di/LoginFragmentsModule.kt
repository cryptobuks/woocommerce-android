package com.woocommerce.android.di

import com.woocommerce.android.ui.login.overrides.WooLoginEmailFragment
import com.woocommerce.android.ui.login.overrides.WooLoginEmailPasswordFragment
import com.woocommerce.android.ui.login.overrides.WooLoginSiteAddressFragment
import com.woocommerce.android.ui.login.overrides.WooLoginUsernamePasswordFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
abstract class LoginFragmentsModule {
    @ContributesAndroidInjector
    abstract fun provideWooLoginSiteAddressFragment(): WooLoginSiteAddressFragment

    @ContributesAndroidInjector
    abstract fun provideWooLoginEmailFragment(): WooLoginEmailFragment

    @ContributesAndroidInjector
    abstract fun provideWooLoginEmailPasswordFragment(): WooLoginEmailPasswordFragment

    @ContributesAndroidInjector
    abstract fun provideWooLoginUsernamePasswordFragment(): WooLoginUsernamePasswordFragment
}
