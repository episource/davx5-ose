/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.Context
import android.provider.CalendarContract
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider

/**
 * Manages automatic synchronization, that is:
 *
 * - synchronization in given intervals, and
 * - synchronization on local data changes.
 *
 * Integrates with both the periodic sync workers and the sync framework. So this class should be used when
 * the caller just wants to update the automatic sync, without needing to know about the underlying details.
 *
 * Automatic synchronization stands in contrast to manual synchronization, which is only triggered by the user.
 */
class AutomaticSyncManager @Inject constructor(
    private val accountSettingsFactory: AccountSettings.Factory,
    @ApplicationContext private val context: Context,
    private val serviceRepository: DavServiceRepository,
    private val syncFramework: SyncFrameworkIntegration,
    private val tasksAppManager: Provider<TasksAppManager>,
    private val workerManager: SyncWorkerManager
) {

    /**
     * Disable automatic synchronization for the given account and data type.
     */
    private fun disableAutomaticSync(account: Account, dataType: SyncDataType) {
        workerManager.disablePeriodic(account, dataType)

        for (authority in dataType.possibleAuthorities(context))
            syncFramework.disableSyncAbility(account, authority)
    }

    /**
     * Enables automatic synchronization for the given account and data type and sets it to the given interval:
     *
     * 1. Sets up periodic sync for the given data type with the given interval.
     * 2. Enables sync in the sync framework for the given data type and sets up periodic sync with the given interval.
     *
     * @param account   the account to synchronize
     * @param dataType  the data type to synchronize
     */
    private fun enableAutomaticSync(
        account: Account,
        dataType: SyncDataType
    ) {
        val accountSettings = accountSettingsFactory.create(account)
        val syncInterval = accountSettings.getSyncInterval(dataType)
        if (syncInterval != null) {
            // update sync workers (needs already updated sync interval in AccountSettings)
            val wifiOnly = accountSettings.getSyncWifiOnly()
            workerManager.enablePeriodic(account, dataType, syncInterval, wifiOnly)
        } else
            workerManager.disablePeriodic(account, dataType)

        // also enable/disable content-triggered syncs
        val possibleAuthorities = dataType.possibleAuthorities(context)
        val authority: String? = when (dataType) {
            SyncDataType.CONTACTS -> context.getString(R.string.address_books_authority)
            SyncDataType.EVENTS -> CalendarContract.AUTHORITY
            SyncDataType.TASKS -> tasksAppManager.get().currentProvider()?.authority
        }
        if (authority != null && syncInterval != null) {
            // enable authority, but completely disable all other possible authorities (for instance, tasks apps which are not the current task app)
            syncFramework.enableSyncOnContentChange(account, authority)
            for (disableAuthority in possibleAuthorities - authority)
                syncFramework.disableSyncAbility(account, disableAuthority)
        } else
            for (authority in possibleAuthorities)
                syncFramework.disableSyncOnContentChange(account, authority)
    }

    /**
     * Updates automatic synchronization of the given account and all data types according to the account settings.
     *
     * If there's a [Service] for the given account and data type, automatic sync is enabled (with details from [AccountSettings]).
     * Otherwise, automatic synchronization is disabled.
     *
     * @param account   account for which automatic synchronization shall be updated
     */
    fun updateAutomaticSync(account: Account) {
        for (dataType in SyncDataType.entries)
            updateAutomaticSync(account, dataType)
    }

    /**
     * Updates automatic synchronization of the given account and data type according to the account services and settings.
     *
     * If there's a [Service] for the given account and data type, automatic sync is enabled (with details from [AccountSettings]).
     * Otherwise, automatic synchronization is disabled.
     *
     * @param account   account for which automatic synchronization shall be updated
     * @param dataType  sync data type for which automatic synchronization shall be updated
     */
    fun updateAutomaticSync(account: Account, dataType: SyncDataType) {
        val serviceType = when (dataType) {
            SyncDataType.CONTACTS -> Service.TYPE_CARDDAV
            SyncDataType.EVENTS,
            SyncDataType.TASKS -> Service.TYPE_CALDAV
        }
        val hasService = serviceRepository.getByAccountAndType(account.name, serviceType) != null

        val hasProvider = if (dataType == SyncDataType.TASKS)
            tasksAppManager.get().currentProvider() != null
        else
            true

        if (hasService && hasProvider)
            enableAutomaticSync(account, dataType)
        else
            disableAutomaticSync(account, dataType)
    }

}