package com.example.pix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Test

class AccountViewModelStoreTest {
    class Marker : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    @Test
    fun sameAccountRetainsModelAndSwitchClearsIt() {
        val stores = AccountViewModelStore()
        val first = ViewModelProvider(stores.forAccount("A"))[Marker::class.java]
        assertSame(first, ViewModelProvider(stores.forAccount("A"))[Marker::class.java])
        val second = ViewModelProvider(stores.forAccount("B"))[Marker::class.java]
        assertTrue(first.cleared)
        assertNotSame(first, second)
        stores.viewModelStore.clear()
    }
}
