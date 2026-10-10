package com.kryogames.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountLogicTest {
    @Test fun authErrorsMatchTheWebsite() {
        assertEquals("Incorrect email or password.", formatAuthError("Invalid login credentials"))
        assertEquals("An account with this email already exists.", formatAuthError("User already registered"))
        assertEquals("Password should be at least 6 characters.", formatAuthError("Password should be at least 6 characters."))
    }

    @Test fun usernameSkipsEmailAndShortValues() {
        assertEquals("Ace", usernameFromMetadata(listOf(null, "a", "Ace")))
        assertEquals("kidx", profileLabel("kidx@mail.com"))
        assertEquals("Player", profileLabel("  "))
    }

    @Test fun profileSearchDropsCharactersThatBreakTheFilter() {
        assertNull(profileSearchQuery("  ", "user"))
        val query = profileSearchQuery("  Ma.ze* ", "user-id")
        assertTrue(query!!.startsWith("select=id,username,avatar&username=ilike.*Maze"))
        assertTrue(query.contains("id=neq.user-id"))
    }

    @Test fun signUpRequiresARealUsername() {
        assertEquals("Username must be at least 2 characters.", validateCredentials(AuthMode.SignUp, "a@b.co", "secret1", "a"))
        assertNull(validateCredentials(AuthMode.SignIn, "a@b.co", "secret1", ""))
        assertEquals(
            "About me must be 280 characters or fewer.",
            validateProfileDraft(ProfileDraft("Ace", "x".repeat(281), "", null, false)),
        )
    }
}
