package com.starfall.gsadrive

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TrashNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sidebarTrashStaysOpenAndCanReturnToFiles() {
        val selected = mutableIntStateOf(0)
        val account = AccountEntry(AccountType.GOOGLE, "test@example.com", "Test")
        compose.setContent {
            MaterialTheme {
                App(
                    model = Model(), selected = selected.intValue,
                    select = { selected.intValue = it }, reload = {}, signIn = {},
                    authorize = {}, connectS3 = { _, _ -> }, signOut = {}, upload = {},
                    createFolder = {}, trash = {},
                    accounts = AccountUi(entries = listOf(account), active = account)
                )
            }
        }
        compose.onNodeWithContentDescription("Open menu").performClick()
        compose.onNodeWithText("Trash").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(3, selected.intValue) }
        compose.onNodeWithText("Trash is empty.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, selected.intValue) }
    }
}
