/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import at.bitfire.davdroid.sync.account.TestAccountAuthenticator
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AccountSettingsTest {

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var accountSettingsFactory: AccountSettings.Factory

    @get:Rule
    val hiltRule = HiltAndroidRule(this)


    @Before
    fun setUp() {
        hiltRule.inject()
    }


    @Test(expected = IllegalArgumentException::class)
    fun testUpdate_MissingMigrations() {
        provideAccount(version = 1) { account ->
            // will run AccountSettings.update
            accountSettingsFactory.create(account, abortOnMissingMigration = true)
        }
    }

    @Test
    fun testUpdate_RunAllMigrations() {
        provideAccount(version = 6) { account ->
            // will run AccountSettings.update
            accountSettingsFactory.create(account, abortOnMissingMigration = true)

            val accountManager = AccountManager.get(context)
            val version = accountManager.getUserData(account, AccountSettings.KEY_SETTINGS_VERSION).toIntOrNull()
            assertEquals(AccountSettings.CURRENT_VERSION, version)
        }
    }


    private fun provideAccount(version: Int, block: (Account) -> Unit) {
        val account = TestAccountAuthenticator.create(version = version)
        try {
            block(account)
        } finally {
            TestAccountAuthenticator.remove(account)
        }
    }

}